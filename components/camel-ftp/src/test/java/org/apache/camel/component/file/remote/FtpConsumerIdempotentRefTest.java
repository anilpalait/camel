/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.file.remote;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.spi.IdempotentRepository;

/**
 * Unit test for the idempotentRepository # option.
 */
public class FtpConsumerIdempotentRefTest extends FtpServerTestSupport {

    private static boolean invoked;

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort()
                + "/idempotent?password=admin&binary=false&idempotent=true&idempotentRepository=#myRepo&delete=true";
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myRepo", new MyIdempotentRepository());
        return jndi;
    }


    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(getFtpUrl()).to("mock:result");
            }
        };
    }

    public void testIdempotent() throws Exception {
        // consume the file the first time
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader(getFtpUrl(), "Hello World", FileComponent.HEADER_FILE_NAME, "report.txt");

        assertMockEndpointsSatisfied();

        Thread.sleep(100);

        // reset mock and set new expectations
        mock.reset();
        mock.expectedMessageCount(0);

        // move file back
        template.sendBodyAndHeader(getFtpUrl(), "Hello World", FileComponent.HEADER_FILE_NAME, "report.txt");

        // should NOT consume the file again, let 2 secs pass to let the consumer try to consume it but it should not
        Thread.sleep(2000);
        assertMockEndpointsSatisfied();

        assertTrue("MyIdempotentRepository should have been invoked", invoked);
    }

    public class MyIdempotentRepository implements IdempotentRepository<String> {

        public boolean add(String messageId) {
            // will return true 1st time, and false 2nd time
            boolean result = invoked;
            invoked = true;
            assertEquals("report.txt", messageId);
            return !result;
        }

        public boolean contains(String key) {
            return invoked;
        }
    }

}