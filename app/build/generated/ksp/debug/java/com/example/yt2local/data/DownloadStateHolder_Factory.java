package com.example.yt2local.data;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class DownloadStateHolder_Factory implements Factory<DownloadStateHolder> {
  @Override
  public DownloadStateHolder get() {
    return newInstance();
  }

  public static DownloadStateHolder_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static DownloadStateHolder newInstance() {
    return new DownloadStateHolder();
  }

  private static final class InstanceHolder {
    static final DownloadStateHolder_Factory INSTANCE = new DownloadStateHolder_Factory();
  }
}
