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
package org.eclipse.che.ide.ext.git.client.plugins;

import static org.eclipse.che.ide.api.vcs.VcsStatus.ADDED;
import static org.eclipse.che.ide.api.vcs.VcsStatus.MODIFIED;
import static org.eclipse.che.ide.api.vcs.VcsStatus.NOT_MODIFIED;
import static org.eclipse.che.ide.api.vcs.VcsStatus.UNTRACKED;

import com.google.inject.Inject;
import com.google.inject.Provider;
import org.eclipse.che.api.git.shared.Status;
import org.eclipse.che.api.project.shared.dto.event.GitChangeEventDto;
import org.eclipse.che.api.project.shared.dto.event.GitCheckoutEventDto;
import org.eclipse.che.ide.api.editor.EditorAgent;
import org.eclipse.che.ide.api.parts.EditorMultiPartStack;
import org.eclipse.che.ide.api.parts.EditorTab;
import org.eclipse.che.ide.api.vcs.VcsStatus;
import org.eclipse.che.ide.ext.git.client.GitEventSubscribable;
import org.eclipse.che.ide.ext.git.client.GitEventsSubscriber;
import org.eclipse.che.ide.resource.Path;

/**
 * Responsible for colorize editor tabs depending on their git status.
 *
 * @author Igor Vinokur
 */
public class EditorTabsColorizer implements GitEventsSubscriber {

  private final Provider<EditorAgent> editorAgentProvider;
  private final Provider<EditorMultiPartStack> multiPartStackProvider;

  @Inject
  public EditorTabsColorizer(
      GitEventSubscribable subscribeToGitEvents,
      Provider<EditorAgent> editorAgentProvider,
      Provider<EditorMultiPartStack> multiPartStackProvider) {
    this.editorAgentProvider = editorAgentProvider;
    this.multiPartStackProvider = multiPartStackProvider;

    subscribeToGitEvents.addSubscriber(this);
  }

  @Override
  public void onFileUnderGitChanged(String endpointId, GitChangeEventDto dto) {
    editorAgentProvider
        .get()
        .getOpenedEditors()
        .stream()
        .filter(
            editor ->
                editor.getEditorInput().getFile().getLocation().equals(Path.valueOf(dto.getPath())))
        .forEach(
            editor -> {
              VcsStatus vcsStatus = VcsStatus.from(dto.getType().toString());
              EditorTab tab = multiPartStackProvider.get().getTabByPart(editor);
              if (vcsStatus != null) {
                tab.setTitleColor(vcsStatus.getColor());
              }
            });
  }

  @Override
  public void onGitStatusChanged(String endpointId, Status status) {
    editorAgentProvider
        .get()
        .getOpenedEditors()
        .forEach(
            editor -> {
              EditorTab tab = multiPartStackProvider.get().getTabByPart(editor);
              String nodeLocation = tab.getFile().getLocation().removeFirstSegments(1).toString();
              if (status.getUntracked().contains(nodeLocation)) {
                tab.setTitleColor(UNTRACKED.getColor());
              } else if (status.getModified().contains(nodeLocation)
                  || status.getChanged().contains(nodeLocation)) {
                tab.setTitleColor(MODIFIED.getColor());
              } else if (status.getAdded().contains(nodeLocation)) {
                tab.setTitleColor(ADDED.getColor());
              } else {
                tab.setTitleColor(NOT_MODIFIED.getColor());
              }
            });
  }

  @Override
  public void onGitCheckout(String endpointId, GitCheckoutEventDto dto) {}
}
