/*
 * Copyright (c) 2012-2017 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.workspace.infrastructure.openshift;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.eclipse.che.api.core.model.workspace.runtime.MachineStatus.FAILED;
import static org.eclipse.che.api.core.model.workspace.runtime.MachineStatus.RUNNING;
import static org.eclipse.che.api.core.model.workspace.runtime.MachineStatus.STARTING;
import static org.eclipse.che.workspace.infrastructure.openshift.provision.UniqueNamesProvisioner.CHE_ORIGINAL_NAME_LABEL;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.IntOrStringBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteSpec;
import io.fabric8.openshift.api.model.RouteTargetReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.eclipse.che.api.core.model.workspace.runtime.MachineStatus;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.installer.server.model.impl.InstallerImpl;
import org.eclipse.che.api.workspace.server.DtoConverter;
import org.eclipse.che.api.workspace.server.URLRewriter;
import org.eclipse.che.api.workspace.server.hc.ServerCheckerFactory;
import org.eclipse.che.api.workspace.server.model.impl.RuntimeIdentityImpl;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.InternalEnvironment;
import org.eclipse.che.api.workspace.server.spi.InternalInfrastructureException;
import org.eclipse.che.api.workspace.server.spi.InternalMachineConfig;
import org.eclipse.che.api.workspace.shared.dto.event.MachineStatusEvent;
import org.eclipse.che.dto.server.DtoFactory;
import org.eclipse.che.workspace.infrastructure.openshift.bootstrapper.OpenShiftBootstrapper;
import org.eclipse.che.workspace.infrastructure.openshift.bootstrapper.OpenShiftBootstrapperFactory;
import org.eclipse.che.workspace.infrastructure.openshift.environment.OpenShiftEnvironment;
import org.eclipse.che.workspace.infrastructure.openshift.project.OpenShiftPersistentVolumeClaims;
import org.eclipse.che.workspace.infrastructure.openshift.project.OpenShiftPods;
import org.eclipse.che.workspace.infrastructure.openshift.project.OpenShiftRoutes;
import org.eclipse.che.workspace.infrastructure.openshift.project.OpenShiftServices;
import org.eclipse.che.workspace.infrastructure.openshift.project.OpenShiftNamespace;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests {@link OpenShiftInternalRuntime}.
 *
 * @author Anton Korneta
 */
public class OpenShiftInternalRuntimeTest {

  private static final int EXPOSED_PORT_1 = 4401;
  private static final int EXPOSED_PORT_2 = 8081;
  private static final int NOT_EXPOSED_PORT_1 = 4411;

  private static final String WORKSPACE_ID = "workspace123";
  private static final String PVC_NAME = "che-workspace-data";
  private static final String POD_NAME = "app";
  private static final String ROUTE_NAME = "test-route";
  private static final String SERVICE_NAME = "test-service";
  private static final String POD_SELECTOR = "che.pod.name";
  private static final String CONTAINER_NAME_1 = "test1";
  private static final String CONTAINER_NAME_2 = "test2";
  private static final String ROUTE_HOST = "localhost";
  private static final String M1_NAME = POD_NAME + '/' + CONTAINER_NAME_1;
  private static final String M2_NAME = POD_NAME + '/' + CONTAINER_NAME_2;

  private static final RuntimeIdentity IDENTITY =
      new RuntimeIdentityImpl(WORKSPACE_ID, "env1", "usr1");

  @Mock private OpenShiftRuntimeContext context;
  @Mock private EventService eventService;
  @Mock private ServerCheckerFactory serverCheckerFactory;
  @Mock private OpenShiftBootstrapperFactory bootstrapperFactory;
  @Mock private OpenShiftEnvironment osEnv;
  @Mock private OpenShiftNamespace namespace;
  @Mock private OpenShiftPersistentVolumeClaims pvcs;
  @Mock private OpenShiftServices services;
  @Mock private OpenShiftRoutes routes;
  @Mock private OpenShiftPods pods;
  @Mock private OpenShiftBootstrapper bootstrapper;
  @Mock private InternalEnvironment environment;

  @Captor private ArgumentCaptor<MachineStatusEvent> eventCaptor;

  private OpenShiftInternalRuntime internalRuntime;

  @BeforeMethod
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    internalRuntime =
        new OpenShiftInternalRuntime(
            context,
            namespace,
            new URLRewriter.NoOpURLRewriter(),
            eventService,
            bootstrapperFactory,
            serverCheckerFactory,
            13);
    when(context.getOpenShiftEnvironment()).thenReturn(osEnv);
    when(context.getIdentity()).thenReturn(IDENTITY);
    doNothing().when(namespace).cleanUp();
    doReturn(ImmutableMap.of(PVC_NAME, mockPvc())).when(osEnv).getPersistentVolumeClaims();
    when(namespace.persistentVolumeClaims()).thenReturn(pvcs);
    when(namespace.services()).thenReturn(services);
    when(namespace.routes()).thenReturn(routes);
    when(namespace.pods()).thenReturn(pods);
    when(pvcs.get()).thenReturn(emptyList());
    when(bootstrapperFactory.create(any(), anyListOf(InstallerImpl.class), any()))
        .thenReturn(bootstrapper);
    when(context.getEnvironment()).thenReturn(environment);
    doReturn(ImmutableMap.of(M1_NAME, mockMachine(), M2_NAME, mockMachine()))
        .when(environment)
        .getMachines();
  }

  @Test
  public void startsOpenShiftEnvironment() throws Exception {
    final Container c1 = mockContainer(CONTAINER_NAME_1, EXPOSED_PORT_1);
    final Container c2 = mockContainer(CONTAINER_NAME_2, EXPOSED_PORT_2, NOT_EXPOSED_PORT_1);
    doReturn(ImmutableMap.of(SERVICE_NAME, mockService())).when(osEnv).getServices();
    doReturn(ImmutableMap.of(SERVICE_NAME, mockRoute())).when(osEnv).getRoutes();
    when(services.create(any())).thenAnswer(a -> a.getArguments()[0]);
    when(routes.create(any())).thenAnswer(a -> a.getArguments()[0]);
    doReturn(ImmutableMap.of(POD_NAME, mockPod(ImmutableList.of(c1, c2)))).when(osEnv).getPods();
    when(pods.create(any())).thenAnswer(a -> a.getArguments()[0]);
    doNothing().when(bootstrapper).bootstrap();

    internalRuntime.internalStart(emptyMap());

    verify(pods, times(1)).create(any());
    verify(routes, times(1)).create(any());
    verify(services, times(1)).create(any());
    verify(bootstrapper, times(2)).bootstrap();
    verify(eventService, times(4)).publish(any());
    verifyEventsOrder(
        newEvent(M1_NAME, STARTING),
        newEvent(M2_NAME, STARTING),
        newEvent(M1_NAME, RUNNING),
        newEvent(M2_NAME, RUNNING));
  }

  @Test(expectedExceptions = InfrastructureException.class)
  public void throwsInfrastructureExceptionWhenPVCsCreationFailed() throws Exception {
    doNothing().when(namespace).cleanUp();
    doThrow(InfrastructureException.class).when(pvcs).get();

    try {
      internalRuntime.internalStart(emptyMap());
    } catch (Exception rethrow) {
      verify(namespace, times(1)).cleanUp();
      verify(namespace, never()).services();
      verify(namespace, never()).routes();
      verify(namespace, never()).pods();
      throw rethrow;
    }
  }

  @Test(expectedExceptions = InternalInfrastructureException.class)
  public void throwsInternalInfrastructureExceptionWhenRuntimeErrorOccurs() throws Exception {
    doNothing().when(namespace).cleanUp();
    final OpenShiftPersistentVolumeClaims pvcs = mock(OpenShiftPersistentVolumeClaims.class);
    when(namespace.persistentVolumeClaims()).thenReturn(pvcs);
    doThrow(RuntimeException.class).when(pvcs).create(any(PersistentVolumeClaim.class));

    try {
      internalRuntime.internalStart(emptyMap());
    } catch (Exception rethrow) {
      verify(namespace, times(1)).cleanUp();
      verify(namespace, never()).services();
      verify(namespace, never()).routes();
      verify(namespace, never()).pods();
      throw rethrow;
    }
  }

  @Test(expectedExceptions = InfrastructureException.class)
  public void stopsWaitingAllMachineStartWhenOneMachineStartFailed() throws Exception {
    final Container c1 = mockContainer(CONTAINER_NAME_1, EXPOSED_PORT_1);
    final Container c2 = mockContainer(CONTAINER_NAME_2, EXPOSED_PORT_2, NOT_EXPOSED_PORT_1);
    doReturn(ImmutableMap.of(SERVICE_NAME, mockService())).when(osEnv).getServices();
    doReturn(ImmutableMap.of(SERVICE_NAME, mockRoute())).when(osEnv).getRoutes();
    when(services.create(any())).thenAnswer(a -> a.getArguments()[0]);
    when(routes.create(any())).thenAnswer(a -> a.getArguments()[0]);
    doReturn(ImmutableMap.of(POD_NAME, mockPod(ImmutableList.of(c1, c2)))).when(osEnv).getPods();
    when(pods.create(any())).thenAnswer(a -> a.getArguments()[0]);
    doThrow(InfrastructureException.class).when(bootstrapper).bootstrap();

    try {
      internalRuntime.internalStart(emptyMap());
    } catch (Exception rethrow) {
      verify(pods, times(1)).create(any());
      verify(routes, times(1)).create(any());
      verify(services, times(1)).create(any());
      verify(bootstrapper, times(1)).bootstrap();
      verify(eventService, times(3)).publish(any());
      verifyEventsOrder(
          newEvent(M1_NAME, STARTING), newEvent(M2_NAME, STARTING), newEvent(M1_NAME, FAILED));
      throw rethrow;
    }
  }

  @Test(expectedExceptions = InfrastructureException.class)
  public void throwsInfrastructureExceptionWhenErrorOccursAndCleanupFailed() throws Exception {
    doNothing().doThrow(InfrastructureException.class).when(namespace).cleanUp();
    doThrow(InfrastructureException.class).when(pvcs).get();

    try {
      internalRuntime.internalStart(emptyMap());
    } catch (Exception rethrow) {
      verify(namespace, times(1)).cleanUp();
      verify(namespace, never()).services();
      verify(namespace, never()).routes();
      verify(namespace, never()).pods();
      throw rethrow;
    }
  }

  @Test(expectedExceptions = InfrastructureException.class)
  public void throwsInfrastructureExceptionWhenBootstrapInterrupted() throws Exception {
    final Container c = mockContainer(CONTAINER_NAME_1, EXPOSED_PORT_1, NOT_EXPOSED_PORT_1);
    doReturn(ImmutableMap.of(SERVICE_NAME, mockService())).when(osEnv).getServices();
    doReturn(ImmutableMap.of(SERVICE_NAME, mockRoute())).when(osEnv).getRoutes();
    when(services.create(any())).thenAnswer(a -> a.getArguments()[0]);
    when(routes.create(any())).thenAnswer(a -> a.getArguments()[0]);
    doReturn(ImmutableMap.of(POD_NAME, mockPod(ImmutableList.of(c)))).when(osEnv).getPods();
    when(pods.create(any())).thenAnswer(a -> a.getArguments()[0]);
    doThrow(InterruptedException.class).when(bootstrapper).bootstrap();

    try {
      internalRuntime.internalStart(emptyMap());
    } catch (Exception rethrow) {
      verify(namespace, times(1)).cleanUp();
      verify(pods, times(1)).create(any());
      verify(routes, times(1)).create(any());
      verify(services, times(1)).create(any());
      verify(bootstrapper, times(1)).bootstrap();
      verifyEventsOrder(newEvent(M1_NAME, STARTING));
      throw rethrow;
    }
  }

  @Test
  public void stopsOpenShiftEnvironment() throws Exception {
    doNothing().when(namespace).cleanUp();

    internalRuntime.internalStop(emptyMap());

    verify(namespace, times(1)).cleanUp();
  }

  @Test(expectedExceptions = InfrastructureException.class)
  public void throwsInfrastructureExceptionWhenOpenShiftProjectCleanupFailed() throws Exception {
    doThrow(InfrastructureException.class).when(namespace).cleanUp();

    internalRuntime.internalStop(emptyMap());
  }

  @Test(expectedExceptions = InfrastructureException.class)
  public void throwsInfrastructureExceptionWhenMachineAbnormallyStopped() throws Exception {
    doThrow(InfrastructureException.class).when(pods).watch(any());

    internalRuntime.internalStart(emptyMap());

    verify(namespace, times(2)).cleanUp();
    verify(namespace, never()).pods();
  }

  private static MachineStatusEvent newEvent(String machineName, MachineStatus status) {
    return DtoFactory.newDto(MachineStatusEvent.class)
        .withIdentity(DtoConverter.asDto(IDENTITY))
        .withMachineName(machineName)
        .withEventType(status);
  }

  private void verifyEventsOrder(MachineStatusEvent... expectedEvents) {
    final Iterator<MachineStatusEvent> actualEvents = captureEvents().iterator();
    for (MachineStatusEvent expected : expectedEvents) {
      if (!actualEvents.hasNext()) {
        fail("It is expected to receive machine status events");
      }
      final MachineStatusEvent actual = actualEvents.next();
      assertEquals(actual, expected);
    }
    if (actualEvents.hasNext()) {
      fail("No more events expected");
    }
  }

  private List<MachineStatusEvent> captureEvents() {
    verify(eventService, atLeastOnce()).publish(eventCaptor.capture());
    return eventCaptor.getAllValues();
  }

  private static Container mockContainer(String name, int... ports) {
    final Container container = mock(Container.class);
    when(container.getName()).thenReturn(name);
    final List<ContainerPort> containerPorts = new ArrayList<>(ports.length);
    for (int port : ports) {
      containerPorts.add(new ContainerPortBuilder().withContainerPort(port).build());
    }
    when(container.getPorts()).thenReturn(containerPorts);
    return container;
  }

  private static Pod mockPod(List<Container> containers) {
    final Pod pod = mock(Pod.class);
    final PodSpec spec = mock(PodSpec.class);
    mockName(POD_NAME, pod);
    when(spec.getContainers()).thenReturn(containers);
    when(pod.getSpec()).thenReturn(spec);
    when(pod.getMetadata().getLabels())
        .thenReturn(ImmutableMap.of(POD_SELECTOR, POD_NAME, CHE_ORIGINAL_NAME_LABEL, POD_NAME));
    return pod;
  }

  private static PersistentVolumeClaim mockPvc() {
    final PersistentVolumeClaim pvc = mock(PersistentVolumeClaim.class);
    mockName(PVC_NAME, pvc);
    return pvc;
  }

  private static Service mockService() {
    final Service service = mock(Service.class);
    final ServiceSpec spec = mock(ServiceSpec.class);
    mockName(SERVICE_NAME, service);
    when(service.getSpec()).thenReturn(spec);
    when(spec.getSelector()).thenReturn(ImmutableMap.of(POD_SELECTOR, POD_NAME));
    final ServicePort sp1 =
        new ServicePortBuilder().withTargetPort(intOrString(EXPOSED_PORT_1)).build();
    final ServicePort sp2 =
        new ServicePortBuilder().withTargetPort(intOrString(EXPOSED_PORT_2)).build();
    when(spec.getPorts()).thenReturn(ImmutableList.of(sp1, sp2));
    return service;
  }

  private static Route mockRoute() {
    final Route route = mock(Route.class);
    mockName(ROUTE_NAME, route);
    final RouteSpec spec = mock(RouteSpec.class);
    final RouteTargetReference target = mock(RouteTargetReference.class);
    when(target.getName()).thenReturn(SERVICE_NAME);
    when(spec.getTo()).thenReturn(target);
    when(spec.getHost()).thenReturn(ROUTE_HOST);
    when(route.getSpec()).thenReturn(spec);
    when(route.getMetadata().getLabels())
        .thenReturn(ImmutableMap.of(CHE_ORIGINAL_NAME_LABEL, ROUTE_NAME));
    return route;
  }

  private static InternalMachineConfig mockMachine(InstallerImpl... installers) {
    final InternalMachineConfig machine1 = mock(InternalMachineConfig.class);
    when(machine1.getInstallers()).thenReturn(Arrays.asList(installers));
    return machine1;
  }

  private static ObjectMeta mockName(String name, HasMetadata mock) {
    final ObjectMeta metadata = mock(ObjectMeta.class);
    when(mock.getMetadata()).thenReturn(metadata);
    when(metadata.getName()).thenReturn(name);
    return metadata;
  }

  private static IntOrString intOrString(int port) {
    return new IntOrStringBuilder().withIntVal(port).withStrVal(String.valueOf(port)).build();
  }
}