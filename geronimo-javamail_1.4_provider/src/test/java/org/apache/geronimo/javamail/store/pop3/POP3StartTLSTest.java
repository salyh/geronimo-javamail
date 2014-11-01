/**
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.geronimo.javamail.store.pop3;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Date;
import java.util.Properties;

import javax.mail.Flags;
import javax.mail.Session;
import javax.mail.Store;

import junit.framework.TestCase;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.james.filesystem.api.mock.MockFileSystem;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.pop3server.core.CoreCmdHandlerLoader;
import org.apache.james.pop3server.netty.POP3Server;
import org.apache.james.protocols.lib.PortUtil;
import org.apache.james.protocols.lib.mock.MockProtocolHandlerLoader;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.lib.mock.MockUsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class POP3StartTLSTest extends TestCase {
    
    public File getAbsoluteFilePathFromClassPath(final String fileNameFromClasspath) {

        File jaasConfigFile = null;
        final URL jaasConfigURL = this.getClass().getClassLoader().getResource(fileNameFromClasspath);
        if (jaasConfigURL != null) {
            try {
                jaasConfigFile = new File(URLDecoder.decode(jaasConfigURL.getFile(), "UTF-8"));
            } catch (final UnsupportedEncodingException e) {
                return null;
            }

            if (jaasConfigFile.exists() && jaasConfigFile.canRead()) {
                return jaasConfigFile;
            } else {
                
                System.out.println("Cannot read from {}, maybe the file does not exists? "+ jaasConfigFile.getAbsolutePath());
            }

        } else {
            System.out.println("Failed to load " + fileNameFromClasspath);
        }

        return null;

    }
    
    public class POP3TestConfiguration extends DefaultConfigurationBuilder {

        /*
         * With socketTLS (SSL/TLS in Thunderbird), all the communication is encrypted.

           With startTLS (STARTTLS in Thunderbird), the preamble is readable, but the rest is encrypted.
         */
        
        private final int pop3ListenerPort;

        public POP3TestConfiguration(int pop3ListenerPort) {
            this.pop3ListenerPort = pop3ListenerPort;
        }

        public void init() {
            addProperty("[@enabled]", true);
            addProperty("bind", "127.0.0.1:" + this.pop3ListenerPort);
            addProperty("tls.[@startTLS]", true);
            addProperty("tls.keystore", "file://"+getAbsoluteFilePathFromClassPath("dummykeystore.jks").getAbsolutePath());
            addProperty("tls.secret", "123456");
            addProperty("tls.provider", "org.bouncycastle.jce.provider.BouncyCastleProvider");
            addProperty("helloName", "myMailServer");
            addProperty("connectiontimeout", "360000");
            addProperty("handlerchain.[@coreHandlersPackage]", CoreCmdHandlerLoader.class.getName());
        }

    }
    
    private POP3Server pop3Server;
    private final int pop3Port = PortUtil.getNonPrivilegedPort();
    private POP3TestConfiguration pop3Configuration;
    private final MockUsersRepository usersRepository = new MockUsersRepository();
    //private POP3Client pop3Client = null;
    protected MockFileSystem fileSystem;
    protected MockProtocolHandlerLoader protocolHandlerChain;
    private StoreMailboxManager<Long> mailboxManager;
    private final byte[] content = ("Return-path: return@test.com\r\n"
            + "Content-Transfer-Encoding: plain\r\n"
            + "Subject: test\r\n\r\n"
            + "Body Text POP3ServerTest.setupTestMails\r\n").getBytes();
    
    
    
    public void test12() throws Exception {
        finishSetUp(pop3Configuration);
        


        //pop3Client = new POP3Client();
        //pop3Client.connect("127.0.0.1", pop3Port);

        usersRepository.addUser("known", "test2");
        
        
        MailboxPath mailboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, "foo2", "INBOX");
        MailboxSession session = mailboxManager.login("known", "test2", LoggerFactory.getLogger("Test"));

        if (!mailboxManager.mailboxExists(mailboxPath, session)) {
            mailboxManager.createMailbox(mailboxPath, session);
        }

        setupTestMails(session, mailboxManager.getMailbox(mailboxPath, session));

        Properties props = new Properties();
        //props.setProperty("mail.pop3s.ssl.trust", "*");
        props.setProperty("mail.store.protocol", "pop3");
        props.setProperty("mail.pop3.port", String.valueOf(pop3Port));
        props.setProperty("mail.debug", "true");
        props.setProperty("mail.pop3.starttls.required", "true");
        props.setProperty("mail.pop3.ssl.trust", "*");
        
        Session jmsession = Session.getInstance(props);
        // Send messages for the current test to GreenMail
        //sendMessage(session, "/messages/multipart.msg");
        //sendMessage(session, "/messages/simple.msg");
        
        // Load the message from POP3
        Store store = jmsession.getStore();
        store.connect("127.0.0.1", "known", "test2");
        
    }
    
    @Override
    public void setUp() throws Exception {
        setUpServiceManager();
        setUpPOP3Server();
        pop3Configuration = new POP3TestConfiguration(pop3Port);
    }
    
    protected POP3Server createPOP3Server() {
        return new POP3Server();
    }

    protected void initPOP3Server(POP3TestConfiguration testConfiguration) throws Exception {
        pop3Server.configure(testConfiguration);
        pop3Server.init();
    }

    
    protected void setUpPOP3Server() {
        pop3Server = createPOP3Server();
        pop3Server.setFileSystem(fileSystem);
        pop3Server.setProtocolHandlerLoader(protocolHandlerChain);
    
        Logger log = LoggerFactory.getLogger("Mock");
        // slf4j can't set programmatically any log level. It's just a facade
        // log.setLevel(SimpleLog.LOG_LEVEL_DEBUG);
        pop3Server.setLog(log);
    }
    
    @Override
    public void tearDown() throws Exception {
       /* try {
            if (pop3Client != null) {
                if (pop3Client.isConnected()) {
                    pop3Client.sendCommand("quit");
                    pop3Client.disconnect();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }*/
        protocolHandlerChain.dispose();
        pop3Server.destroy();
    }

    protected void finishSetUp(POP3TestConfiguration testConfiguration) throws Exception {
        testConfiguration.init();
        initPOP3Server(testConfiguration);
    }

    private void setupTestMails(MailboxSession session, MessageManager mailbox) throws MailboxException {
        mailbox.appendMessage(new ByteArrayInputStream(content), new Date(), session, true, new Flags());
        byte[] content2 = ("EMPTY").getBytes();
        mailbox.appendMessage(new ByteArrayInputStream(content2), new Date(), session, true, new Flags());
    }
    
    protected void setUpServiceManager() throws Exception {
        protocolHandlerChain = new MockProtocolHandlerLoader();
        protocolHandlerChain.put("usersrepository", usersRepository);
    
        InMemoryMailboxSessionMapperFactory factory = new InMemoryMailboxSessionMapperFactory();
        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
        mailboxManager = new StoreMailboxManager<Long>(factory, new Authenticator() {
            
            public boolean isAuthentic(String userid, CharSequence passwd) {
                try {
                    return usersRepository.test(userid, passwd.toString());
                } catch (UsersRepositoryException e) {
                    e.printStackTrace();
                    return false;
                }
            }
        }, aclResolver, groupMembershipResolver);
        mailboxManager.init();

        protocolHandlerChain.put("mailboxmanager", mailboxManager);
    
        fileSystem = new MockFileSystem();
        protocolHandlerChain.put("fileSystem", fileSystem);
    
    }
    

    

    
}
