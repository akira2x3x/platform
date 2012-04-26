/*
 * Copyright (c) WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.bps.integration.tests.humantask;

import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.databinding.types.URI;
import org.apache.commons.lang.StringUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.Test;
import org.wso2.bps.integration.tests.util.FrameworkSettings;
import org.wso2.bps.integration.tests.util.HumanTaskTestConstants;
import org.wso2.carbon.humantask.stub.ui.task.client.api.TaskOperationsStub;
import org.wso2.carbon.humantask.stub.ui.task.client.api.types.TComment;
import org.wso2.carbon.humantask.stub.ui.task.client.api.types.TTaskAbstract;
import org.wso2.carbon.humantask.stub.ui.task.client.api.types.TTaskEvent;
import org.wso2.carbon.humantask.stub.ui.task.client.api.types.TTaskEvents;
import org.wso2.carbon.utils.CarbonUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Test class to check the task operations functionality.
 */
public class TaskOperationsTestCase {

    private TaskOperationsStub taskOperationsStub = null;

    private URI taskId = null;

    private Set<String> taskEvents = new HashSet<String>();


    @BeforeGroups(groups = {"wso2.bps"}, description = " Copying sample HumanTask packages")
    public void init() throws Exception {
        initTaskOperationServiceStub();
        taskId = new URI("1");
    }

    private void initTaskOperationServiceStub() throws Exception {

        String TASK_OPERATIONS_SERVICE_URL = "https://" + FrameworkSettings.HOST_NAME +
                                             ":" + FrameworkSettings.HTTPS_PORT +
                                             "/services/taskOperations";
        taskOperationsStub = new TaskOperationsStub(TASK_OPERATIONS_SERVICE_URL);

        ServiceClient serviceClient = taskOperationsStub._getServiceClient();
        CarbonUtils.setBasicAccessSecurityHeaders(HumanTaskTestConstants.CLERK1_USER,
                                                  HumanTaskTestConstants.CLERK1_PASSWORD, serviceClient);
        Options serviceClientOptions = serviceClient.getOptions();
        serviceClientOptions.setManageSession(true);

    }

    @Test(groups = {"wso2.bps"}, description = "Claims approval test case")
    public void testLoadTask()
            throws Exception {

        TTaskAbstract loadedTask = taskOperationsStub.loadTask(taskId);
        Assert.assertNotNull(loadedTask, "The task is not created successfully");
        Assert.assertEquals(loadedTask.getId().toString(), "1", "The task id is wrong");

    }

    @Test(groups = {"wso2.bps"}, description = "Claims approval test case")
    public void taskClaimTask()
            throws Exception {

        taskOperationsStub.claim(taskId);
        TTaskAbstract loadedTask = taskOperationsStub.loadTask(taskId);
        Assert.assertEquals(loadedTask.getActualOwner().getTUser(), HumanTaskTestConstants.CLERK1_USER,
                            "The assignee should be clerk1 !");
        Assert.assertEquals(loadedTask.getStatus().toString(), "RESERVED",
                            "The task status should be RESERVED!");

        taskEvents.add("claim");

        // Now reclaim the task to continue with other operations.

    }

    @Test(groups = {"wso2.bps"}, description = "Claims approval test case release and reclaim task")
    public void taskReleaseAndReClaimTask()
            throws Exception {

        taskOperationsStub.release(taskId);
        TTaskAbstract loadedTask = taskOperationsStub.loadTask(taskId);

        //Now as the task have been release
        //1. The actual user value should be empty.
        Assert.assertNull(loadedTask.getActualOwner(),
                          "After releasing the task the actual owner should be null");
        //2. The task status should go back to READY
        Assert.assertEquals(loadedTask.getStatus().toString(), "READY",
                            "The task status should be READY!");

        taskEvents.add("release");

        // Now reclaim the task to continue with other operations.
        taskOperationsStub.claim(taskId);
        TTaskAbstract loadedTaskAferReClaim = taskOperationsStub.loadTask(taskId);
        Assert.assertEquals(loadedTaskAferReClaim.getActualOwner().getTUser(), HumanTaskTestConstants.CLERK1_USER,
                            "The assignee should be clerk1 !");
        Assert.assertEquals(loadedTaskAferReClaim.getStatus().toString(), "RESERVED",
                            "The task status should be RESERVED!");


    }

    @Test(groups = {"wso2.bps"}, description = "Claims approval test case")
    public void testStartTask()
            throws Exception {

        taskOperationsStub.start(taskId);
        TTaskAbstract loadedTask = taskOperationsStub.loadTask(taskId);
        Assert.assertEquals(loadedTask.getStatus().toString(), "IN_PROGRESS",
                            "The task status should be IN_PROGRESS after starting the task!");
        taskEvents.add("start");
    }

    @Test(groups = {"wso2.bps"}, description = "Claims approval test case")
    public void testStopTask()
            throws Exception {

        taskOperationsStub.stop(taskId);
        TTaskAbstract loadedTask = taskOperationsStub.loadTask(taskId);
        Assert.assertEquals(loadedTask.getStatus().toString(), "RESERVED",
                            "The task status should be RESERVED after stopping the task!");
        taskEvents.add("stop");

        // Now start the task again
        taskOperationsStub.start(taskId);
        TTaskAbstract loadedTask2 = taskOperationsStub.loadTask(taskId);
        Assert.assertEquals(loadedTask2.getStatus().toString(), "IN_PROGRESS",
                            "The task status should be IN_PROGRESS after re-starting the task!");
    }

    @Test(groups = {"wso2.bps"}, description = "Claims approval test case")
    public void testSuspendAndResume()
            throws Exception {

        taskOperationsStub.suspend(taskId);
        TTaskAbstract loadedTask = taskOperationsStub.loadTask(taskId);
        Assert.assertEquals(loadedTask.getStatus().toString(), "SUSPENDED",
                            "The task status should be SUSPENDED after suspending the task!");
        Assert.assertEquals(loadedTask.getPreviousStatus().toString(), "IN_PROGRESS",
                            "The task previous status should be IN_PROGRESS");
        taskEvents.add("suspend");

        taskOperationsStub.resume(taskId);
        TTaskAbstract loadedTaskAfterResume = taskOperationsStub.loadTask(taskId);
        Assert.assertEquals(loadedTaskAfterResume.getStatus().toString(), "IN_PROGRESS",
                            "The task status should be IN_PROGRESS after resuming the suspended task!");
        taskEvents.add("resume");
    }

    @Test(groups = {"wso2.bps"}, description = "Claims approval test case")
    public void testTaskCommentOperations() throws Exception {
        String commentText1 = "This is a test comment";
        URI taskCommentId = taskOperationsStub.addComment(taskId, commentText1);

        taskEvents.add("addcomment");

        Assert.assertNotNull(taskCommentId, "The comment id cannot be null");

        TComment[] taskComments = taskOperationsStub.getComments(taskId);
        Assert.assertEquals(taskComments.length, 1, "The task comments size should be 1 after adding only 1 comment");
        Assert.assertEquals(taskComments[0].getId(), taskCommentId, "The task comment id returned should be equal");

        String commentText2 = "This is a test comment 2";
        URI taskCommentId2 = taskOperationsStub.addComment(taskId, commentText2);
        Assert.assertNotNull(taskCommentId2, "The comment id cannot be null");

        TComment[] taskComments2 = taskOperationsStub.getComments(taskId);
        Assert.assertEquals(taskComments2.length, 2, "The task comments size should be 2 after adding 2 comments");
        Assert.assertEquals(taskComments2[1].getId(), taskCommentId2, "The task comment id returned should be equal");

        //delete the comments
        taskOperationsStub.deleteComment(taskId, taskCommentId);
        TComment[] commentsAfterDeletion = taskOperationsStub.getComments(taskId);
        Assert.assertEquals(commentsAfterDeletion.length, 1, "Only 1 comment should be left");
        Assert.assertEquals(commentsAfterDeletion[0].getId(), taskCommentId2, "Only comment 2 should be left after deleting comment 1");

        //delete the left over comment as well
        taskOperationsStub.deleteComment(taskId, taskCommentId2);
        TComment[] commentsAfterAllDeletions = taskOperationsStub.getComments(taskId);
        Assert.assertNull(commentsAfterAllDeletions, "There should not be any comments left!");

        taskEvents.add("deletecomment");

    }

    // check the task events are persisted properly
    @Test(groups = {"wso2.bps"}, description = "Task event persistence")
    public void testTaskEventHistory() throws Exception {

        TTaskEvents tTaskEvents = taskOperationsStub.loadTaskEvents(taskId);
        TTaskEvent[] events = tTaskEvents.getEvent();

        Assert.assertNotNull(events, "The task event history cannot be empty after performing task operations");
        Assert.assertNotEquals(events.length, 0, "The task event history objects should be a positive number");

        Set<String> persistedTaskEvents = new HashSet<String>();
        for (TTaskEvent event : events) {
            persistedTaskEvents.add(event.getEventType());
        }

        for (String occurredTaskEvent : this.taskEvents) {
            Assert.assertTrue(persistedTaskEvents.contains(occurredTaskEvent),
                               "The occurred task event [" + occurredTaskEvent +
                               "] is not in the persisted task event list :[" +
                               StringUtils.join(persistedTaskEvents.toArray(), ",") + "]");
        }

    }

}
