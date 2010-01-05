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

package org.hornetq.jms.management.impl;

import java.util.List;

import javax.management.MBeanInfo;
import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import org.hornetq.api.jms.HornetQConnectionFactory;
import org.hornetq.api.jms.management.ConnectionFactoryControl;
import org.hornetq.api.jms.management.JMSQueueControl;
import org.hornetq.core.management.impl.MBeanInfoHelper;

/**
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>
 * 
 * @version <tt>$Revision$</tt>
 * 
 */
public class JMSConnectionFactoryControlImpl extends StandardMBean implements ConnectionFactoryControl
{
   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   private final HornetQConnectionFactory cf;

   private final List<String> bindings;

   private final String name;

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   public JMSConnectionFactoryControlImpl(final HornetQConnectionFactory cf,
                                          final String name,
                                          final List<String> bindings) throws NotCompliantMBeanException
   {
      super(ConnectionFactoryControl.class);
      this.cf = cf;
      this.name = name;
      this.bindings = bindings;
   }

   // Public --------------------------------------------------------

   // ManagedConnectionFactoryMBean implementation ------------------

   public List<String> getBindings()
   {
      return bindings;
   }

   public String getClientID()
   {
      return cf.getClientID();
   }

   public long getClientFailureCheckPeriod()
   {
      return cf.getClientFailureCheckPeriod();
   }

   public long getCallTimeout()
   {
      return cf.getCallTimeout();
   }

   public int getConsumerMaxRate()
   {
      return cf.getConsumerMaxRate();
   }

   public int getConsumerWindowSize()
   {
      return cf.getConsumerWindowSize();
   }

   public int getProducerMaxRate()
   {
      return cf.getProducerMaxRate();
   }

   public int getConfirmationWindowSize()
   {
      return cf.getConfirmationWindowSize();
   }

   public int getDupsOKBatchSize()
   {
      return cf.getDupsOKBatchSize();
   }

   public boolean isBlockOnAcknowledge()
   {
      return cf.isBlockOnAcknowledge();
   }

   public boolean isBlockOnNonDurableSend()
   {
      return cf.isBlockOnNonDurableSend();
   }

   public boolean isBlockOnDurableSend()
   {
      return cf.isBlockOnDurableSend();
   }

   public boolean isPreAcknowledge()
   {
      return cf.isPreAcknowledge();
   }

   public String getName()
   {
      return name;
   }

   public long getConnectionTTL()
   {
      return cf.getConnectionTTL();
   }

   public int getReconnectAttempts()
   {
      return cf.getReconnectAttempts();
   }

   public boolean isFailoverOnServerShutdown()
   {
      return cf.isFailoverOnServerShutdown();
   }

   public long getMinLargeMessageSize()
   {
      return cf.getMinLargeMessageSize();
   }

   public long getRetryInterval()
   {
      return cf.getRetryInterval();
   }

   public double getRetryIntervalMultiplier()
   {
      return cf.getRetryIntervalMultiplier();
   }

   public long getTransactionBatchSize()
   {
      return cf.getTransactionBatchSize();
   }

   public boolean isAutoGroup()
   {
      return cf.isAutoGroup();
   }

   @Override
   public MBeanInfo getMBeanInfo()
   {
      MBeanInfo info = super.getMBeanInfo();
      return new MBeanInfo(info.getClassName(),
                           info.getDescription(),
                           info.getAttributes(),
                           info.getConstructors(),
                           MBeanInfoHelper.getMBeanOperationsInfo(ConnectionFactoryControl.class),
                           info.getNotifications());
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------
}
