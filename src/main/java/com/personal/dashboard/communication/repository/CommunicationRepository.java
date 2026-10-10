package com.personal.dashboard.communication.repository;

import com.personal.dashboard.communication.entity.CommunicationRecords.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Account-scoped identities and atomic one-shot action claims; no external calls here. */
@Repository
public class CommunicationRepository {
  private final JdbcTemplate jdbc;

  public CommunicationRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<AccountRecord> accounts() {
    return jdbc.query(
        "SELECT * FROM communication_accounts ORDER BY created_at,id",
        (r, i) ->
            new AccountRecord(
                r.getString("id"),
                r.getString("provider"),
                r.getString("external_id"),
                r.getString("label"),
                r.getString("credential_cipher"),
                r.getString("capabilities"),
                r.getLong("created_at")));
  }

  public void saveAccount(AccountRecord account) {
    jdbc.update(
        "INSERT INTO communication_accounts(id,provider,external_id,label,credential_cipher,capabilities,created_at) VALUES(?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET label=excluded.label,credential_cipher=excluded.credential_cipher,capabilities=excluded.capabilities",
        account.id(),
        account.provider(),
        account.externalId(),
        account.label(),
        account.credentialCipher(),
        account.capabilities(),
        account.createdAt());
  }

  public boolean replaceCredential(String id, String expectedCipher, String replacementCipher) {
    return jdbc.update(
            "UPDATE communication_accounts SET credential_cipher=? WHERE id=? AND credential_cipher=?",
            replacementCipher,
            id,
            expectedCipher)
        == 1;
  }

  public void deleteAccount(String id) {
    jdbc.update("DELETE FROM communication_accounts WHERE id=?", id);
  }

  public void cache(
      String accountId, String conversationId, String messageId, Long timestamp, String payload) {
    jdbc.update(
        "INSERT INTO communication_messages(account_id,conversation_id,message_id,sent_at,payload) VALUES(?,?,?,?,?) ON CONFLICT(account_id,conversation_id,message_id) DO UPDATE SET sent_at=excluded.sent_at,payload=excluded.payload",
        accountId,
        conversationId,
        messageId,
        timestamp,
        payload);
  }

  public void removeMessage(String accountId, String conversationId, String messageId) {
    jdbc.update(
        "DELETE FROM communication_messages WHERE account_id=? AND conversation_id=? AND message_id=?",
        accountId,
        conversationId,
        messageId);
  }

  public boolean receiveEvent(String eventId) {
    jdbc.update(
        "DELETE FROM communication_event_receipts WHERE received_at<?",
        System.currentTimeMillis() - 86400000);
    return jdbc.update(
            "INSERT OR IGNORE INTO communication_event_receipts(event_id,received_at) VALUES(?,?)",
            eventId,
            System.currentTimeMillis())
        == 1;
  }

  public Optional<String> cachedMessage(String accountId, String conversationId, String messageId) {
    return jdbc
        .query(
            "SELECT payload FROM communication_messages WHERE account_id=? AND conversation_id=? AND message_id=?",
            (r, i) -> r.getString(1),
            accountId,
            conversationId,
            messageId)
        .stream()
        .findFirst();
  }

  public List<String> cachedMessages(String accountId, String conversationId) {
    return jdbc.query(
        "SELECT payload FROM (SELECT payload,sent_at,message_id FROM communication_messages WHERE account_id=? AND conversation_id=? ORDER BY sent_at DESC,message_id DESC LIMIT 200) ORDER BY sent_at,message_id",
        (r, i) -> r.getString(1),
        accountId,
        conversationId);
  }

  public List<String> search(String query) {
    return jdbc.query(
        "SELECT payload FROM communication_messages WHERE instr(lower(payload),lower(?))>0 ORDER BY sent_at DESC,message_id LIMIT 100",
        (r, i) -> r.getString(1),
        query);
  }

  public Optional<String> cursor(String accountId, String conversationId) {
    return jdbc
        .query(
            "SELECT cursor FROM communication_cursors WHERE account_id=? AND conversation_id=?",
            (r, i) -> r.getString(1),
            accountId,
            conversationId)
        .stream()
        .findFirst();
  }

  public void removeMessage(String accountId, String messageId) {
    jdbc.update(
        "DELETE FROM communication_messages WHERE account_id=? AND message_id=?",
        accountId,
        messageId);
  }

  public void clearMessages(String accountId) {
    jdbc.update("DELETE FROM communication_messages WHERE account_id=?", accountId);
  }

  public void cursor(String accountId, String conversationId, String cursor) {
    jdbc.update(
        "INSERT INTO communication_cursors(account_id,conversation_id,cursor,synchronized_at) VALUES(?,?,?,?) ON CONFLICT(account_id,conversation_id) DO UPDATE SET cursor=excluded.cursor,synchronized_at=excluded.synchronized_at",
        accountId,
        conversationId,
        cursor,
        System.currentTimeMillis());
  }

  public void createAction(PendingActionRecord action) {
    jdbc.update(
        "INSERT INTO communication_actions(id,account_id,payload_cipher,state,expires_at,result_id) VALUES(?,?,?,?,?,?)",
        action.id(),
        action.accountId(),
        action.payloadCipher(),
        action.state(),
        action.expiresAt(),
        action.resultId());
  }

  public List<PendingActionRecord> actions() {
    return jdbc.query(
        "SELECT * FROM communication_actions WHERE expires_at>? ORDER BY expires_at",
        (r, i) ->
            new PendingActionRecord(
                r.getString("id"),
                r.getString("account_id"),
                r.getString("payload_cipher"),
                r.getString("state"),
                r.getLong("expires_at"),
                r.getString("result_id")),
        System.currentTimeMillis());
  }

  public boolean claim(String id) {
    return jdbc.update(
            "UPDATE communication_actions SET state='SENDING' WHERE id=? AND state='PENDING' AND expires_at>?",
            id,
            System.currentTimeMillis())
        == 1;
  }

  public void finish(String id, String state, String result) {
    jdbc.update(
        "UPDATE communication_actions SET state=?,result_id=? WHERE id=?", state, result, id);
  }

  public void cancel(String id) {
    jdbc.update(
        "UPDATE communication_actions SET state='CANCELLED' WHERE id=? AND state='PENDING'", id);
  }

  public void cleanup() {
    jdbc.update("DELETE FROM communication_actions WHERE expires_at<?", System.currentTimeMillis());
  }
}
