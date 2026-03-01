package com.example.yt2local;

import android.content.Context;
import com.example.yt2local.data.DownloadStateHolder;
import com.example.yt2local.data.db.DownloadHistoryDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
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
public final class MainViewModel_Factory implements Factory<MainViewModel> {
  private final Provider<VideoRepository> repositoryProvider;

  private final Provider<DownloadStateHolder> downloadStateHolderProvider;

  private final Provider<DownloadHistoryDao> historyDaoProvider;

  private final Provider<Context> contextProvider;

  private MainViewModel_Factory(Provider<VideoRepository> repositoryProvider,
      Provider<DownloadStateHolder> downloadStateHolderProvider,
      Provider<DownloadHistoryDao> historyDaoProvider, Provider<Context> contextProvider) {
    this.repositoryProvider = repositoryProvider;
    this.downloadStateHolderProvider = downloadStateHolderProvider;
    this.historyDaoProvider = historyDaoProvider;
    this.contextProvider = contextProvider;
  }

  @Override
  public MainViewModel get() {
    return newInstance(repositoryProvider.get(), downloadStateHolderProvider.get(), historyDaoProvider.get(), contextProvider.get());
  }

  public static MainViewModel_Factory create(Provider<VideoRepository> repositoryProvider,
      Provider<DownloadStateHolder> downloadStateHolderProvider,
      Provider<DownloadHistoryDao> historyDaoProvider, Provider<Context> contextProvider) {
    return new MainViewModel_Factory(repositoryProvider, downloadStateHolderProvider, historyDaoProvider, contextProvider);
  }

  public static MainViewModel newInstance(VideoRepository repository,
      DownloadStateHolder downloadStateHolder, DownloadHistoryDao historyDao, Context context) {
    return new MainViewModel(repository, downloadStateHolder, historyDao, context);
  }
}
