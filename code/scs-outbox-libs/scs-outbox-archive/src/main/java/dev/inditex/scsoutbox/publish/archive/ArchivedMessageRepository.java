package dev.inditex.scsoutbox.publish.archive;

public interface ArchivedMessageRepository {

  void save(final ArchivedMessage archivedMessage);

}
