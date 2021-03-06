/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.hornetq.tests.integration.client;
import org.junit.Before;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.Message;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientProducer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.client.MessageHandler;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.server.HornetQServers;
import org.hornetq.core.server.Queue;
import org.hornetq.core.settings.impl.AddressSettings;
import org.hornetq.tests.integration.IntegrationTestLogger;
import org.hornetq.tests.util.RandomUtil;
import org.hornetq.tests.util.ServiceTestBase;
import org.hornetq.tests.util.UnitTestCase;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class DeadLetterAddressTest extends ServiceTestBase
{
   private static final IntegrationTestLogger log = IntegrationTestLogger.LOGGER;

   private HornetQServer server;

   private ClientSession clientSession;
   private ServerLocator locator;

   @Test
   public void testBasicSend() throws Exception
   {
      SimpleString dla = new SimpleString("DLA");
      SimpleString qName = new SimpleString("q1");
      SimpleString adName = new SimpleString("ad1");
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setMaxDeliveryAttempts(1);
      addressSettings.setDeadLetterAddress(dla);
      server.getAddressSettingsRepository().addMatch(adName.toString(), addressSettings);
      SimpleString dlq = new SimpleString("DLQ1");
      clientSession.createQueue(dla, dlq, null, false);
      clientSession.createQueue(adName, qName, null, false);
      ClientProducer producer = clientSession.createProducer(adName);
      producer.send(createTextMessage(clientSession, "heyho!"));
      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(qName);
      ClientMessage m = clientConsumer.receive(500);
      m.acknowledge();
      Assert.assertNotNull(m);
      Assert.assertEquals(m.getBodyBuffer().readString(), "heyho!");
      // force a cancel
      clientSession.rollback();
      m = clientConsumer.receiveImmediate();
      Assert.assertNull(m);
      clientConsumer.close();
      clientConsumer = clientSession.createConsumer(dlq);
      m = clientConsumer.receive(500);
      Assert.assertNotNull(m);
      assertEquals("q1", m.getStringProperty(Message.HDR_ORIGINAL_QUEUE));
      assertEquals("ad1", m.getStringProperty(Message.HDR_ORIGINAL_ADDRESS));
      Assert.assertEquals(m.getBodyBuffer().readString(), "heyho!");
   }

   // HORNETQ- 1084
   @Test
   public void testBasicSendWithDLAButNoBinding() throws Exception
   {
      SimpleString dla = new SimpleString("DLA");
      SimpleString qName = new SimpleString("q1");
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setMaxDeliveryAttempts(1);
      addressSettings.setDeadLetterAddress(dla);
      server.getAddressSettingsRepository().addMatch(qName.toString(), addressSettings);
      //SimpleString dlq = new SimpleString("DLQ1");
      //clientSession.createQueue(dla, dlq, null, false);
      clientSession.createQueue(qName, qName, null, false);
      ClientProducer producer = clientSession.createProducer(qName);
      producer.send(createTextMessage(clientSession, "heyho!"));
      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(qName);
      ClientMessage m = clientConsumer.receive(500);
      m.acknowledge();
      Assert.assertNotNull(m);
      Assert.assertEquals(m.getBodyBuffer().readString(), "heyho!");
      // force a cancel
      clientSession.rollback();
      m = clientConsumer.receiveImmediate();
      Assert.assertNull(m);
      clientConsumer.close();
      Queue q = (Queue)server.getPostOffice().getBinding(qName).getBindable();
      Assert.assertEquals(0, q.getDeliveringCount());
   }

   @Test
   public void testBasicSend2times() throws Exception
   {
      SimpleString dla = new SimpleString("DLA");
      SimpleString qName = new SimpleString("q1");
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setMaxDeliveryAttempts(2);
      addressSettings.setDeadLetterAddress(dla);
      server.getAddressSettingsRepository().addMatch(qName.toString(), addressSettings);
      SimpleString dlq = new SimpleString("DLQ1");
      clientSession.createQueue(dla, dlq, null, false);
      clientSession.createQueue(qName, qName, null, false);
      ClientProducer producer = clientSession.createProducer(qName);
      producer.send(createTextMessage(clientSession, "heyho!"));
      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(qName);
      ClientMessage m = clientConsumer.receive(5000);
      m.acknowledge();
      Assert.assertNotNull(m);
      Assert.assertEquals(m.getBodyBuffer().readString(), "heyho!");
      // force a cancel
      clientSession.rollback();
      clientSession.start();
      m = clientConsumer.receive(5000);
      m.acknowledge();
      Assert.assertNotNull(m);
      Assert.assertEquals(m.getBodyBuffer().readString(), "heyho!");
      // force a cancel
      clientSession.rollback();
      m = clientConsumer.receiveImmediate();
      Assert.assertNull(m);
      clientConsumer.close();
      clientConsumer = clientSession.createConsumer(dlq);
      m = clientConsumer.receive(5000);
      Assert.assertNotNull(m);
      Assert.assertEquals(m.getBodyBuffer().readString(), "heyho!");
   }

   @Test
   public void testReceiveWithListeners() throws Exception
   {
      SimpleString dla = new SimpleString("DLA");
      SimpleString qName = new SimpleString("q1");
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setMaxDeliveryAttempts(2);
      addressSettings.setDeadLetterAddress(dla);
      server.getAddressSettingsRepository().addMatch(qName.toString(), addressSettings);
      SimpleString dlq = new SimpleString("DLQ1");
      clientSession.createQueue(dla, dlq, null, false);
      clientSession.createQueue(qName, qName, null, false);
      ClientProducer producer = clientSession.createProducer(qName);
      producer.send(createTextMessage(clientSession, "heyho!"));
      ClientConsumer clientConsumer = clientSession.createConsumer(qName);
      final CountDownLatch latch = new CountDownLatch(2);
      TestHandler handler = new TestHandler(latch, clientSession);
      clientConsumer.setMessageHandler(handler);
      clientSession.start();
      assertTrue(latch.await(5, TimeUnit.SECONDS));
      assertEquals(handler.count, 2);
      clientConsumer = clientSession.createConsumer(dlq);
      Message m = clientConsumer.receive(5000);
      Assert.assertNotNull(m);
      Assert.assertEquals(m.getBodyBuffer().readString(), "heyho!");
   }

   class  TestHandler implements MessageHandler
   {
      private final CountDownLatch latch;
      int count = 0;

      private final ClientSession clientSession;

      public TestHandler(CountDownLatch latch, ClientSession clientSession)
      {
         this.latch = latch;
         this.clientSession = clientSession;
      }

      public void onMessage(ClientMessage message)
      {
         count++;
         latch.countDown();
         try
         {
            clientSession.rollback(true);
         }
         catch (HornetQException e)
         {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
         }
         throw new RuntimeException();
      }
   }

   @Test
   public void testBasicSendToMultipleQueues() throws Exception
   {
      SimpleString dla = new SimpleString("DLA");
      SimpleString qName = new SimpleString("q1");
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setMaxDeliveryAttempts(1);
      addressSettings.setDeadLetterAddress(dla);
      server.getAddressSettingsRepository().addMatch(qName.toString(), addressSettings);
      SimpleString dlq = new SimpleString("DLQ1");
      SimpleString dlq2 = new SimpleString("DLQ2");
      clientSession.createQueue(dla, dlq, null, false);
      clientSession.createQueue(dla, dlq2, null, false);
      clientSession.createQueue(qName, qName, null, false);
      ClientProducer producer = clientSession.createProducer(qName);
      producer.send(createTextMessage(clientSession, "heyho!"));
      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(qName);
      ClientMessage m = clientConsumer.receive(500);
      m.acknowledge();
      Assert.assertNotNull(m);
      Assert.assertEquals(m.getBodyBuffer().readString(), "heyho!");
      // force a cancel
      clientSession.rollback();
      m = clientConsumer.receiveImmediate();
      Assert.assertNull(m);
      clientConsumer.close();
      clientConsumer = clientSession.createConsumer(dlq);
      m = clientConsumer.receive(500);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "heyho!");
      clientConsumer.close();
      clientConsumer = clientSession.createConsumer(dlq2);
      m = clientConsumer.receive(500);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "heyho!");
      clientConsumer.close();
   }

   @Test
   public void testBasicSendToNoQueue() throws Exception
   {
      SimpleString qName = new SimpleString("q1");
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setMaxDeliveryAttempts(1);
      server.getAddressSettingsRepository().addMatch(qName.toString(), addressSettings);
      clientSession.createQueue(qName, qName, null, false);
      ClientProducer producer = clientSession.createProducer(qName);
      producer.send(createTextMessage(clientSession, "heyho!"));
      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(qName);
      ClientMessage m = clientConsumer.receive(500);
      m.acknowledge();
      Assert.assertNotNull(m);
      Assert.assertEquals(m.getBodyBuffer().readString(), "heyho!");
      // force a cancel
      clientSession.rollback();
      m = clientConsumer.receiveImmediate();
      Assert.assertNull(m);
      clientConsumer.close();
   }

   @Test
   public void testHeadersSet() throws Exception
   {
      final int MAX_DELIVERIES = 16;
      final int NUM_MESSAGES = 5;
      SimpleString dla = new SimpleString("DLA");
      SimpleString qName = new SimpleString("q1");
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setMaxDeliveryAttempts(MAX_DELIVERIES);
      addressSettings.setDeadLetterAddress(dla);
      server.getAddressSettingsRepository().addMatch(qName.toString(), addressSettings);
      SimpleString dlq = new SimpleString("DLQ1");
      clientSession.createQueue(dla, dlq, null, false);
      clientSession.createQueue(qName, qName, null, false);
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession sendSession = sessionFactory.createSession(false, true, true);
      ClientProducer producer = sendSession.createProducer(qName);
      Map<String, Long> origIds = new HashMap<String, Long>();

      for (int i = 0; i < NUM_MESSAGES; i++)
      {
         ClientMessage tm = createTextMessage(clientSession, "Message:" + i);
         producer.send(tm);
      }

      ClientConsumer clientConsumer = clientSession.createConsumer(qName);
      clientSession.start();

      for (int i = 0; i < MAX_DELIVERIES; i++)
      {
         for (int j = 0; j < NUM_MESSAGES; j++)
         {
            ClientMessage tm = clientConsumer.receive(1000);

            Assert.assertNotNull(tm);
            tm.acknowledge();
            if (i == 0)
            {
               origIds.put("Message:" + j, tm.getMessageID());
            }
            Assert.assertEquals("Message:" + j, tm.getBodyBuffer().readString());
         }
         clientSession.rollback();
      }

      long timeout = System.currentTimeMillis() + 5000;

      // DLA transfer is asynchronous fired on the rollback
      while (System.currentTimeMillis() < timeout && ((Queue)server.getPostOffice().getBinding(qName).getBindable()).getMessageCount() != 0)
      {
         Thread.sleep(1);
      }

      Assert.assertEquals(0, ((Queue)server.getPostOffice().getBinding(qName).getBindable()).getMessageCount());
      ClientMessage m = clientConsumer.receiveImmediate();
      Assert.assertNull(m);
      // All the messages should now be in the DLQ

      ClientConsumer cc3 = clientSession.createConsumer(dlq);

      for (int i = 0; i < NUM_MESSAGES; i++)
      {
         ClientMessage tm = cc3.receive(1000);

         Assert.assertNotNull(tm);

         String text = tm.getBodyBuffer().readString();
         Assert.assertEquals("Message:" + i, text);

         // Check the headers
         SimpleString origDest = (SimpleString)tm.getObjectProperty(Message.HDR_ORIGINAL_ADDRESS);

         Long origMessageId = (Long)tm.getObjectProperty(Message.HDR_ORIG_MESSAGE_ID);

         Assert.assertEquals(qName, origDest);

         Long origId = origIds.get(text);

         Assert.assertEquals(origId, origMessageId);
      }

      sendSession.close();

   }

   @Test
   public void testDeadlLetterAddressWithDefaultAddressSettings() throws Exception
   {
      int deliveryAttempt = 3;

      SimpleString address = RandomUtil.randomSimpleString();
      SimpleString queue = RandomUtil.randomSimpleString();
      SimpleString deadLetterAdress = RandomUtil.randomSimpleString();
      SimpleString deadLetterQueue = RandomUtil.randomSimpleString();
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setMaxDeliveryAttempts(deliveryAttempt);
      addressSettings.setDeadLetterAddress(deadLetterAdress);
      server.getAddressSettingsRepository().setDefault(addressSettings);

      clientSession.createQueue(address, queue, false);
      clientSession.createQueue(deadLetterAdress, deadLetterQueue, false);

      ClientProducer producer = clientSession.createProducer(address);
      ClientMessage clientMessage = createTextMessage(clientSession, "heyho!");
      producer.send(clientMessage);

      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(queue);
      for (int i = 0; i < deliveryAttempt; i++)
      {
         ClientMessage m = clientConsumer.receive(500);
         Assert.assertNotNull(m);
         DeadLetterAddressTest.log.info("i is " + i);
         DeadLetterAddressTest.log.info("delivery cout is " + m.getDeliveryCount());
         Assert.assertEquals(i + 1, m.getDeliveryCount());
         m.acknowledge();
         clientSession.rollback();
      }
      ClientMessage m = clientConsumer.receive(500);
      Assert.assertNull("not expecting a message", m);
      clientConsumer.close();

      clientConsumer = clientSession.createConsumer(deadLetterQueue);
      m = clientConsumer.receive(500);
      Assert.assertNotNull(m);
      Assert.assertEquals(m.getBodyBuffer().readString(), "heyho!");
   }

   @Test
   public void testDeadlLetterAddressWithWildcardAddressSettings() throws Exception
   {
      int deliveryAttempt = 3;

      SimpleString address = RandomUtil.randomSimpleString();
      SimpleString queue = RandomUtil.randomSimpleString();
      SimpleString deadLetterAdress = RandomUtil.randomSimpleString();
      SimpleString deadLetterQueue = RandomUtil.randomSimpleString();
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setMaxDeliveryAttempts(deliveryAttempt);
      addressSettings.setDeadLetterAddress(deadLetterAdress);
      server.getAddressSettingsRepository().addMatch("*", addressSettings);

      clientSession.createQueue(address, queue, false);
      clientSession.createQueue(deadLetterAdress, deadLetterQueue, false);

      ClientProducer producer = clientSession.createProducer(address);
      ClientMessage clientMessage = createTextMessage(clientSession, "heyho!");
      producer.send(clientMessage);

      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(queue);
      for (int i = 0; i < deliveryAttempt; i++)
      {
         ClientMessage m = clientConsumer.receive(500);
         Assert.assertNotNull(m);
         Assert.assertEquals(i + 1, m.getDeliveryCount());
         m.acknowledge();
         clientSession.rollback();
      }
      ClientMessage m = clientConsumer.receiveImmediate();
      Assert.assertNull(m);
      clientConsumer.close();

      clientConsumer = clientSession.createConsumer(deadLetterQueue);
      m = clientConsumer.receive(500);
      Assert.assertNotNull(m);
      Assert.assertEquals(m.getBodyBuffer().readString(), "heyho!");
   }

   @Test
   public void testDeadLetterAddressWithOverridenSublevelAddressSettings() throws Exception
   {
      int defaultDeliveryAttempt = 3;
      int specificeDeliveryAttempt = defaultDeliveryAttempt + 1;

      SimpleString address = new SimpleString("prefix.address");
      SimpleString queue = RandomUtil.randomSimpleString();
      SimpleString defaultDeadLetterAddress = RandomUtil.randomSimpleString();
      SimpleString defaultDeadLetterQueue = RandomUtil.randomSimpleString();
      SimpleString specificDeadLetterAddress = RandomUtil.randomSimpleString();
      SimpleString specificDeadLetterQueue = RandomUtil.randomSimpleString();

      AddressSettings defaultAddressSettings = new AddressSettings();
      defaultAddressSettings.setMaxDeliveryAttempts(defaultDeliveryAttempt);
      defaultAddressSettings.setDeadLetterAddress(defaultDeadLetterAddress);
      server.getAddressSettingsRepository().addMatch("*", defaultAddressSettings);
      AddressSettings specificAddressSettings = new AddressSettings();
      specificAddressSettings.setMaxDeliveryAttempts(specificeDeliveryAttempt);
      specificAddressSettings.setDeadLetterAddress(specificDeadLetterAddress);
      server.getAddressSettingsRepository().addMatch(address.toString(), specificAddressSettings);

      clientSession.createQueue(address, queue, false);
      clientSession.createQueue(defaultDeadLetterAddress, defaultDeadLetterQueue, false);
      clientSession.createQueue(specificDeadLetterAddress, specificDeadLetterQueue, false);

      ClientProducer producer = clientSession.createProducer(address);
      ClientMessage clientMessage = createTextMessage(clientSession, "heyho!");
      producer.send(clientMessage);

      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(queue);
      ClientConsumer defaultDeadLetterConsumer = clientSession.createConsumer(defaultDeadLetterQueue);
      ClientConsumer specificDeadLetterConsumer = clientSession.createConsumer(specificDeadLetterQueue);

      for (int i = 0; i < defaultDeliveryAttempt; i++)
      {
         ClientMessage m = clientConsumer.receive(500);
         Assert.assertNotNull(m);
         Assert.assertEquals(i + 1, m.getDeliveryCount());
         m.acknowledge();
         clientSession.rollback();
      }

      Assert.assertNull(defaultDeadLetterConsumer.receiveImmediate());
      Assert.assertNull(specificDeadLetterConsumer.receiveImmediate());

      // one more redelivery attempt:
      ClientMessage m = clientConsumer.receive(500);
      Assert.assertNotNull(m);
      Assert.assertEquals(specificeDeliveryAttempt, m.getDeliveryCount());
      m.acknowledge();
      clientSession.rollback();

      Assert.assertNull(defaultDeadLetterConsumer.receiveImmediate());
      Assert.assertNotNull(specificDeadLetterConsumer.receive(500));
   }

   @Override
   @Before
   public void setUp() throws Exception
   {
      super.setUp();

      Configuration configuration = createDefaultConfig();
      configuration.setSecurityEnabled(false);
      TransportConfiguration transportConfig = new TransportConfiguration(UnitTestCase.INVM_ACCEPTOR_FACTORY);
      configuration.getAcceptorConfigurations().add(transportConfig);
      server = addServer(HornetQServers.newHornetQServer(configuration, false));
      // start the server
      server.start();
      // then we create a client as normal
      locator =
               addServerLocator(HornetQClient.createServerLocatorWithoutHA(new TransportConfiguration(
                                                                                                      UnitTestCase.INVM_CONNECTOR_FACTORY)));
      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      clientSession = addClientSession(sessionFactory.createSession(false, true, false));
   }
}
