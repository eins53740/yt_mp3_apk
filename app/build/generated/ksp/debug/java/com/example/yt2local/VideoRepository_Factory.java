package com.example.yt2local;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
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
public final class VideoRepository_Factory implements Factory<VideoRepository> {
  private final Provider<Context> contextProvider;

  private VideoRepository_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public VideoRepository get() {
    return newInstance(contextProvider.get());
  }

  public static VideoRepository_Factory create(Provider<Context> contextProvider) {
    return new VideoRepository_Factory(contextProvider);
  }

  public static VideoRepository newInstance(Context context) {
    return new VideoRepository(context);
  }
}
