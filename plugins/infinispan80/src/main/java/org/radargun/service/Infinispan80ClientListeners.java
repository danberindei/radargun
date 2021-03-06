package org.radargun.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.annotation.ClientCacheEntryExpired;
import org.infinispan.client.hotrod.annotation.ClientListener;
import org.infinispan.client.hotrod.event.ClientCacheEntryExpiredEvent;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * Generic Infinispan remote listeners for Infinispan 8, similar to {@link InfinispanCacheListeners}.
 *
 * @author Martin Gencur &lt;mgencur@redhat.com&gt;
 */
public class Infinispan80ClientListeners extends InfinispanClientListeners {

   protected final ConcurrentMap<String, GenericInfinispan80ClientListener> listeners = new ConcurrentHashMap<String, GenericInfinispan80ClientListener>();

   public Infinispan80ClientListeners(Infinispan80HotrodService service) {
      super(service);
   }

   @Override
   protected GenericInfinispan80ClientListener getOrCreateListener(String cacheName, boolean sync) {
      if (cacheName == null)
         cacheName = service.getRemoteManager(false).getCache().getName();

      GenericInfinispan80ClientListener listenerContainer = listeners.get(cacheName);
      if (listenerContainer == null) {
         listenerContainer = new GenericInfinispan80ClientListener();
         listeners.put(cacheName, listenerContainer);
         service.getRemoteManager(false).getCache(cacheName).addClientListener(listenerContainer);
         service.getRemoteManager(true).getCache(cacheName).addClientListener(listenerContainer);
      }
      return listenerContainer;
   }

   @Override
   protected GenericInfinispan80ClientListener getListenerOrThrow(String cacheName, boolean sync) {
      if (cacheName == null) {
         final RemoteCacheManager remoteManager = service.getRemoteManager(false);
         cacheName = remoteManager.getCache().getName();
      }
      GenericInfinispan80ClientListener listenerContainer = listeners.get(cacheName);
      if (listenerContainer == null)
         throw new IllegalArgumentException("No listener was registered on cache " + cacheName);
      return listenerContainer;
   }

   @Override
   public Collection<Type> getSupportedListeners() {
      return Arrays.asList(Type.CREATED, Type.UPDATED, Type.REMOVED, Type.EXPIRED);
   }

   @Override
   public void addExpiredListener(String cacheName, ExpiredListener listener, boolean sync) {
      getOrCreateListener(cacheName, sync).add(listener);
   }

   @Override
   public void removeExpiredListener(String cacheName, ExpiredListener listener, boolean sync) {
      getListenerOrThrow(cacheName, sync).remove(listener);
      removeListener(cacheName, sync);
   }

   @ClientListener
   public static class GenericInfinispan80ClientListener extends GenericClientListener {

      protected static final Log log = LogFactory.getLog(GenericInfinispan80ClientListener.class);

      @ClientCacheEntryExpired
      public void expired(ClientCacheEntryExpiredEvent e) {
         for (ExpiredListener listener : expired) {
            try {
               listener.expired(e.getKey(), null);
            } catch (Exception ex) {
               log.error("Listener " + listener + " has thrown an exception", ex);
            }
         }
      }
   }
}
