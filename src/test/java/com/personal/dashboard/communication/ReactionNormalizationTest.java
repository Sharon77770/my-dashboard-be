package com.personal.dashboard.communication;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.communication.adapter.DiscordProvider;
import com.personal.dashboard.communication.adapter.SlackProvider;
import com.personal.dashboard.communication.dto.CommunicationDto.MessageView;
import org.junit.jupiter.api.Test;

class ReactionNormalizationTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void slackUsesReportedCountRatherThanIncompleteUsersList() throws Exception {
    var message =
        SlackProvider.normalize(
            "C1",
            json.readTree(
                """
        {"ts":"1700000000.000001","reactions":[
          {"name":"facepalm","count":1034,"users":["U1","U2"]},
          {"name":"unknown","users":["U1"]},
          {"name":"invalid","count":-1}]}
        """));
    assertThat(message.reactions().getFirst().count()).isEqualTo(1034L);
    assertThat(message.reactions().get(1).count()).isNull();
    assertThat(message.reactions().get(2).count()).isNull();
    assertThat(message.unread()).isNull();
  }

  @Test
  void discordRetainsCustomEmojiIdAndUnknownNameOrCountWithoutGuessing() throws Exception {
    var message =
        DiscordProvider.normalize(
            "C1",
            json.readTree(
                """
        {"id":"m1","reactions":[
          {"emoji":{"id":null,"name":"wave"},"count":2},
          {"emoji":{"id":"123","name":"party"},"count":8},
          {"emoji":{"id":"456","name":null}},
          {"emoji":{},"count":99}]}
        """));
    assertThat(message.reactions()).hasSize(3);
    assertThat(message.reactions().get(1).key()).isEqualTo("123");
    assertThat(message.reactions().get(1).label()).isEqualTo("party");
    assertThat(message.reactions().get(2).label()).isEqualTo("456");
    assertThat(message.reactions().get(2).count()).isNull();
    assertThat(message.unread()).isNull();
  }

  @Test
  void oldCachedMessagesRemainReadableWithoutReactionField() throws Exception {
    var message = json.readValue("{\"id\":\"legacy\",\"attachments\":[]}", MessageView.class);
    assertThat(message.id()).isEqualTo("legacy");
    assertThat(message.reactions()).isEmpty();
    assertThat(message.unread()).isNull();
  }
}
