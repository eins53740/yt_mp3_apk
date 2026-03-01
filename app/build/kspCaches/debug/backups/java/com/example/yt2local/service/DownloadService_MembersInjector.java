package com.example.yt2local.service;

import com.example.yt2local.VideoRepository;
import com.example.yt2local.data.DownloadStateHolder;
import com.example.yt2local.data.db.DownloadHistoryDao;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

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
public final class DownloadService_MembersInjector implements MembersInjector<DownloadService> {
  private final Provider<VideoRepository> repositoryProvider;

  private final Provider<DownloadStateHolder> downloadStateHolderProvider;

  private final Provider<DownloadHistoryDao> historyDaoProvider;

  private DownloadService_MembersInjector(Provider<VideoRepository> repositoryProvider,
      Provider<DownloadStateHolder> downloadStateHolderProvider,
      Provider<DownloadHistoryDao> historyDaoProvider) {
    this.repositoryProvider = repositoryProvider;
    this.downloadStateHolderProvider = downloadStateHolderProvider;
    this.historyDaoProvider = historyDaoProvider;
  }

  @Override
  public void injectMembers(DownloadService instance) {
    injectRepository(instance, repositoryProvider.get());
    injectDownloadStateHolder(instance, downloadStateHolderProvider.get());
    injectHistoryDao(instance, historyDaoProvider.get());
  }

  public static MembersInjector<DownloadService> create(
      Provider<VideoRepository> repositoryProvider,
      Provider<DownloadStateHolder> downloadStateHolderProvider,
      Provider<DownloadHistoryDao> historyDaoProvider) {
    return new DownloadService_MembersInjector(repositoryProvider, downloadStateHolderProvider, historyDaoProvider);
  }

  @InjectedFieldSignature("com.example.yt2local.service.DownloadService.repository")
  public static void injectRepository(DownloadService instance, VideoRepository repository) {
    instance.repository = repository;
  }

  @InjectedFieldSignature("com.example.yt2local.service.DownloadService.downloadStateHolder")
  public static void injectDownloadStateHolder(DownloadService instance,
      DownloadStateHolder downloadStateHolder) {
    instance.downloadStateHolder = downloadStateHolder;
  }

  @InjectedFieldSignature("com.example.yt2local.service.DownloadService.historyDao")
  public static void injectHistoryDao(DownloadService instance, DownloadHistoryDao historyDao) {
    instance.historyDao = historyDao;
  }
}
