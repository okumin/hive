package org.apache.hadoop.hive.metastore;

import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.metastore.api.Partition;
import org.apache.hadoop.hive.metastore.api.TableMeta;
import org.apache.hadoop.hive.metastore.utils.MetaStoreUtils;
import org.apache.thrift.TException;

public abstract class NormalizedMetaStoreClient implements IMetaStoreClient {
  private final Configuration conf;

  public NormalizedMetaStoreClient(Configuration conf) {
    this.conf = conf;
  }

  // Mark as final so as to disallow Hive developers or vendors to override variants
  @Override
  public final Partition appendPartition(String dbName, String tableName, List<String> partVals) throws TException {
    return appendPartition(MetaStoreUtils.getDefaultCatalog(conf), dbName, tableName, partVals);
  }

  // Vendors implement this normalized method
  // public Partition appendPartition(String catName, String dbName, String tableName, List<String> partVals);

  // Mark as final so as to disallow Hive developers or vendors to override variants
  @Override
  public final Partition appendPartition(String dbName, String tableName, String name) throws TException {
    return appendPartition(MetaStoreUtils.getDefaultCatalog(conf), dbName, tableName, name);
  }

  // Vendors implement this normalized method
  // public Partition appendPartition(String catName, String dbName, String tableName, String name);

  // or we newly add the following method as the most generic variant, and deprecate all the other variants
  // public Partition appendPartition(AppendPartitionsRequest appendPartitionsRequest) throws TException;

  // Mark as final so as to disallow Hive developers or vendors to override variants
  @Override
  public final List<TableMeta> getTableMeta(String dbPatterns, String tablePatterns, List<String> tableTypes)
      throws TException {
    return getTableMeta(MetaStoreUtils.getDefaultCatalog(conf), dbPatterns, tablePatterns, tableTypes);
  }

  // Vendors implement this normalized method
  // public List<TableMeta> getTableMeta(String catName, String dbPatterns, String tablePatterns, List<String> tableTypes);
}
