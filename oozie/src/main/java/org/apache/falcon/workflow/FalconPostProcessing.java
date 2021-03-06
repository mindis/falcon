/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.falcon.workflow;

import org.apache.falcon.logging.JobLogMover;
import org.apache.falcon.messaging.JMSMessageProducer;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility called by oozie workflow engine post workflow execution in parent workflow.
 */
public class FalconPostProcessing extends Configured implements Tool {
    private static final Logger LOG = LoggerFactory.getLogger(FalconPostProcessing.class);

    public static void main(String[] args) throws Exception {
        ToolRunner.run(new Configuration(), new FalconPostProcessing(), args);
    }

    @Override
    public int run(String[] args) throws Exception {

        WorkflowExecutionContext context = WorkflowExecutionContext.create(args,
                WorkflowExecutionContext.Type.POST_PROCESSING);
        LOG.info("Post workflow execution context created {}", context);
        // serialize the context to HDFS under logs dir before sending the message
        context.serialize();

        LOG.info("Sending user message {} ", context);
        invokeUserMessageProducer(context);

        // JobLogMover doesn't throw exception, a failed log mover will not fail the user workflow
        LOG.info("Moving logs {}", context);
        invokeLogProducer(context);

        LOG.info("Sending falcon message {}", context);
        invokeFalconMessageProducer(context);

        return 0;
    }

    private void invokeUserMessageProducer(WorkflowExecutionContext context) throws Exception {
        JMSMessageProducer jmsMessageProducer = JMSMessageProducer.builder(context)
                .type(JMSMessageProducer.MessageType.USER)
                .build();
        jmsMessageProducer.sendMessage(WorkflowExecutionContext.USER_MESSAGE_ARGS);
    }

    private void invokeFalconMessageProducer(WorkflowExecutionContext context) throws Exception {
        JMSMessageProducer jmsMessageProducer = JMSMessageProducer.builder(context)
                .type(JMSMessageProducer.MessageType.FALCON)
                .build();
        jmsMessageProducer.sendMessage();
    }

    private void invokeLogProducer(WorkflowExecutionContext context) {
        // todo: need to move this out to Falcon in-process
        if (UserGroupInformation.isSecurityEnabled()) {
            LOG.info("Unable to move logs as security is enabled.");
            return;
        }

        try {
            new JobLogMover().run(context);
        } catch (Exception ignored) {
            // Mask exception, a failed log mover will not fail the user workflow
            LOG.error("Exception in job log mover:", ignored);
        }
    }
}
