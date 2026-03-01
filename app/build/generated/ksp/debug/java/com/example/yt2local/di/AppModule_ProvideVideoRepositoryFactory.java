package com.example.yt2local.di;

import android.content.Context;
import com.example.yt2local.VideoRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class AppModule_ProvideVideoRepositoryFactory implements Factory<VideoRepository> {
  private final Provider<Context> contextProvider;

  private AppModule_ProvideVideoRepositoryFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public VideoRepository get() {
    return provideVideoRepository(contextProvider.get());
  }

  public static AppModule_ProvideVideoRepositoryFactory create(Provider<Context> contextProvider) {
    return new AppModule_ProvideVideoRepositoryFactory(contextProvider);
  }

  public static VideoRepository provideVideoRepository(Context context) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideVideoRepository(context));
  }
}
