package org.apache.hadoop.hive.ql.metadata;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.conf.HiveConf.ConfVars;
import org.apache.hadoop.hive.metastore.ExceptionHandler;
import org.apache.hadoop.hive.metastore.HiveMetaHookLoader;
import org.apache.hadoop.hive.metastore.IMetaStoreClient;
import org.apache.hadoop.hive.metastore.api.MetaException;
import org.apache.hadoop.hive.metastore.conf.MetastoreConf;
import org.apache.hadoop.hive.metastore.utils.JavaUtils;

public class HiveMetaStoreClientFactory {
  public static IMetaStoreClient createMetaStoreClient(HiveConf conf, HiveMetaHookLoader hookLoader,
      boolean allowEmbedded, ConcurrentHashMap<String, Long> metaCallTimeMap) throws MetaException {
    checkNotNull(conf, "conf cannot be null!");
    checkNotNull(hookLoader, "hookLoader cannot be null!");
    checkNotNull(metaCallTimeMap, "metaCallTimeMap cannot be null!");

    String clientClassName = MetastoreConf.getVar(conf, MetastoreConf.ConfVars.METASTORE_CLIENT_CLASS);

    if (clientClassName.equals(SessionHiveMetaStoreClient.class.getName())
        && conf.getBoolVar(ConfVars.METASTORE_FASTPATH)) {
      // METASTORE_FASTPATH is a parameter only for SessionHiveMetaStoreClient
      return new SessionHiveMetaStoreClient(conf, hookLoader, allowEmbedded);
    }

    String proxyClassName = MetastoreConf.getVar(conf, MetastoreConf.ConfVars.METASTORE_CLIENT_PROXY_CLASS);
    if (proxyClassName == null) {
      // Without a proxy
      Class<? extends IMetaStoreClient> baseClass = JavaUtils.getClass(clientClassName, IMetaStoreClient.class);
      return JavaUtils.newInstance(
          baseClass,
          new Class[] { Configuration.class, HiveMetaHookLoader.class, Boolean.class },
          new Object[] { conf, hookLoader, allowEmbedded });
    }

    return getProxy(conf, hookLoader, metaCallTimeMap, clientClassName, allowEmbedded, proxyClassName);
  }

  private static IMetaStoreClient getProxy(Configuration hiveConf, HiveMetaHookLoader hookLoader,
      ConcurrentHashMap<String, Long> metaCallTimeMap, String mscClassName, boolean allowEmbedded,
      String proxyClassName) throws MetaException {
    return getProxy(hiveConf,
        new Class[] {Configuration.class, HiveMetaHookLoader.class, Boolean.class},
        new Object[] {hiveConf, hookLoader, allowEmbedded},
        metaCallTimeMap,
        mscClassName,
        proxyClassName
    );
  }

  private static IMetaStoreClient getProxy(Configuration hiveConf, Class<?>[] constructorArgTypes,
      Object[] constructorArgs, ConcurrentHashMap<String, Long> metaCallTimeMap,
      String mscClassName, String proxyClassName) throws MetaException {

    @SuppressWarnings("unchecked")
    Class<? extends IMetaStoreClient> baseClass =
        JavaUtils.getClass(mscClassName, IMetaStoreClient.class);

    Class<? extends InvocationHandler> proxy = JavaUtils.getClass(proxyClassName, InvocationHandler.class);
    try {
      InvocationHandler handler = JavaUtils.newInstance(
          proxy,
          new Class[] { Configuration.class, Class[].class, Object[].class, ConcurrentHashMap.class, Class.class },
          new Object[] { hiveConf, constructorArgTypes, constructorArgs, metaCallTimeMap, baseClass }
      );
      return (IMetaStoreClient) Proxy.newProxyInstance(proxy.getClassLoader(), baseClass.getInterfaces(), handler);
    } catch (Throwable t) {
      Throwable rootCause = ExceptionUtils.getRootCause(t);
      if (rootCause instanceof Exception) {
        throw ExceptionHandler.newMetaException((Exception) rootCause);
      }
      throw t;
    }
  }
}
