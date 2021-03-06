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
package org.eclipse.che.selenium.refactor.types;

import com.google.inject.Inject;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.eclipse.che.commons.lang.NameGenerator;
import org.eclipse.che.selenium.core.client.TestProjectServiceClient;
import org.eclipse.che.selenium.core.constant.TestMenuCommandsConstants;
import org.eclipse.che.selenium.core.project.ProjectTemplates;
import org.eclipse.che.selenium.core.utils.WaitUtils;
import org.eclipse.che.selenium.core.workspace.TestWorkspace;
import org.eclipse.che.selenium.pageobject.AskDialog;
import org.eclipse.che.selenium.pageobject.CodenvyEditor;
import org.eclipse.che.selenium.pageobject.Consoles;
import org.eclipse.che.selenium.pageobject.Ide;
import org.eclipse.che.selenium.pageobject.Loader;
import org.eclipse.che.selenium.pageobject.Menu;
import org.eclipse.che.selenium.pageobject.ProjectExplorer;
import org.eclipse.che.selenium.pageobject.Refactor;
import org.openqa.selenium.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/** @author Musienko Maxim */
public class RenameTypeTest {
  private static final Logger LOG = LoggerFactory.getLogger(RenameTypeTest.class);
  private static final String nameOfProject =
      NameGenerator.generate(RenameTypeTest.class.getName(), 2);
  private static final String pathToPackageInChePrefix =
      nameOfProject + "/src" + "/main" + "/java" + "/renametype";

  private String pathToCurrentPackage;
  private String contentFromInA;
  private String contentFromOutB;

  @Inject private TestWorkspace workspace;
  @Inject private Ide ide;
  @Inject private ProjectExplorer projectExplorer;
  @Inject private Loader loader;
  @Inject private CodenvyEditor editor;
  @Inject private Refactor refactorPanel;
  @Inject private Consoles consoles;
  @Inject private Menu menu;
  @Inject private AskDialog askDialog;
  @Inject private TestProjectServiceClient testProjectServiceClient;

  @BeforeClass
  public void setup() throws Exception {
    URL resource = RenameTypeTest.this.getClass().getResource("/projects/RenameType");
    testProjectServiceClient.importProject(
        workspace.getId(),
        Paths.get(resource.toURI()),
        nameOfProject,
        ProjectTemplates.MAVEN_SIMPLE);
    ide.open(workspace);
    projectExplorer.waitVisibleItem(nameOfProject);
    consoles.closeProcessesArea();
    projectExplorer.quickExpandWithJavaScript();
    loader.waitOnClosed();
  }

  @BeforeMethod
  public void setCurrentFieldForTest(Method method) throws IOException {
    try {
      setFieldsForTest(method.getName());
    } catch (Exception e) {
      LOG.error(e.getLocalizedMessage(), e);
    }
  }

  @AfterMethod
  public void closeForm() {
    try {
      if (refactorPanel.isWidgetOpened()) {
        loader.waitOnClosed();
        refactorPanel.clickCancelButtonRefactorForm();
      }
      editor.closeAllTabs();
    } catch (Exception e) {
      LOG.error(e.getLocalizedMessage(), e);
    }
  }

  @Test
  public void test0() {
    testCase();
  }

  @Test(priority = 1)
  public void test1() {
    testCase();
  }

  @Test(priority = 2)
  public void test2() {
    testCase();
  }

  @Test(priority = 3)
  public void test3() {
    testCase();
  }

  @Test(priority = 4)
  public void test4() {
    testCase();
  }

  @Test(priority = 5)
  public void test5() {
    testCase();
  }

  @Test(priority = 6)
  public void test6() {
    testCase();
  }

  @Test(priority = 7)
  public void test7() {
    testCase();
  }

  @Test(priority = 8)
  public void test8() {
    testCase();
  }

  @Test(priority = 9)
  public void test9() {
    testCase();
  }

  private void setFieldsForTest(String nameCurrentTest) throws Exception {
    pathToCurrentPackage = pathToPackageInChePrefix + "/" + nameCurrentTest;

    URL resourcesInA =
        getClass()
            .getResource(
                "/org/eclipse/che/selenium/refactor/types/" + nameCurrentTest + "/in/A.java");
    URL resourcesOutA =
        getClass()
            .getResource(
                "/org/eclipse/che/selenium/refactor/types/" + nameCurrentTest + "/out/B.java");

    contentFromInA = getTextFromFile(resourcesInA);
    contentFromOutB = getTextFromFile(resourcesOutA);
  }

  private String getTextFromFile(URL url) throws Exception {
    String result = "";
    List<String> listWithAllLines =
        Files.readAllLines(Paths.get(url.toURI()), Charset.forName("UTF-8"));
    for (String buffer : listWithAllLines) {
      result += buffer + '\n';
    }

    return result;
  }

  private void testCase() {
    projectExplorer.openItemByPath(pathToCurrentPackage + "/A.java");
    editor.waitTextIntoEditor(contentFromInA);
    projectExplorer.selectItem(pathToCurrentPackage + "/A.java");
    menu.runCommand(
        TestMenuCommandsConstants.Assistant.ASSISTANT,
        TestMenuCommandsConstants.Assistant.Refactoring.REFACTORING,
        TestMenuCommandsConstants.Assistant.Refactoring.RENAME);

    refactorPanel.typeNewName("B.java");

    try {
      refactorPanel.clickOkButtonRefactorForm();
    } catch (org.openqa.selenium.TimeoutException ex) {
      //For response from server side if a net works too slowly
      WaitUtils.sleepQuietly(1);
      refactorPanel.typeNewName("B.java");
      refactorPanel.sendKeysIntoField(Keys.ARROW_LEFT.toString());
      refactorPanel.sendKeysIntoField(Keys.ARROW_LEFT.toString());
      refactorPanel.clickOkButtonRefactorForm();
    }
    askDialog.waitFormToOpen();
    askDialog.clickOkBtn();
    askDialog.waitFormToClose();
    refactorPanel.waitRefactorPreviewFormIsClosed();
    projectExplorer.waitItem(pathToCurrentPackage + "/B.java", 6);
    editor.waitTextIntoEditor(contentFromOutB);
  }
}
