package com.personal.dashboard.communication;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.communication.adapter.DiscordProvider;
import com.personal.dashboard.communication.adapter.ProviderHttpClient;
import org.junit.jupiter.api.Test;

/** Official response fixtures; these tests do not connect a live Discord account. */
class DiscordProviderTest {
  private final ObjectMapper json = new ObjectMapper();
  private final ProviderHttpClient http = mock(ProviderHttpClient.class);
  private final DiscordProvider provider = new DiscordProvider(http);

  private void response(String path, String body) throws Exception {
    when(http.get("https://discord.com/api/v10" + path, "Bot fixture"))
        .thenReturn(json.readTree(body));
  }

  @Test
  void listsReturnedThreadsWithOriginalIdsAndForumParentWithoutInventingContainers()
      throws Exception {
    response("/users/@me/guilds?limit=10&after=", "[{\"id\":\"g1\",\"name\":\"Guild\"}]");
    response(
        "/guilds/g1/channels",
        """
        [{"id":"text","name":"general","type":0},
         {"id":"news","name":"announcements","type":5},
         {"id":"forum","name":"help","type":15},
         {"id":"voice","name":"voice","type":2}]
        """);
    response(
        "/guilds/g1/threads/active",
        """
        {"threads":[{"id":"post","name":"Question","type":11,"parent_id":"forum"},
         {"id":"private","name":"Returned private thread","type":12,"parent_id":"text"},
         {"id":"post","name":"Question","type":11,"parent_id":"forum"},
         {"id":"invalid","name":"Not a thread","type":0}]}
        """);
    var page = provider.listConversations("fixture", "", "");
    assertThat(page.items())
        .extracting(item -> item.id())
        .containsExactly("text", "news", "post", "private");
    assertThat(page.items().get(2).kind()).isEqualTo("THREAD");
    assertThat(page.items().get(2).title()).isEqualTo("Guild / help / Question");
    assertThat(page.nextCursor()).isEmpty();
    assertThat(provider.listConversations("fixture", "", "QUESTION").items())
        .extracting(item -> item.id())
        .containsExactly("post");
    response("/channels/post/messages?limit=50", "[{\"id\":\"m1\",\"content\":\"answer\"}]");
    var messages = provider.fetchMessages("fixture", "post", "");
    assertThat(messages.items().getFirst().conversationId()).isEqualTo("post");
    assertThat(messages.items().getFirst().id()).isEqualTo("m1");
  }

  @Test
  void keepsGuildPaginationEvenWhenFilteredThreadsHaveNoMatches() throws Exception {
    var guilds = json.createArrayNode();
    for (int index = 0; index < 10; index++) {
      guilds.addObject().put("id", "g" + index).put("name", "Guild");
      response("/guilds/g" + index + "/channels", "[]");
      response("/guilds/g" + index + "/threads/active", "{\"threads\":[]}");
    }
    response("/users/@me/guilds?limit=10&after=previous", guilds.toString());
    var page = provider.listConversations("fixture", "previous", "missing");
    assertThat(page.items()).isEmpty();
    assertThat(page.nextCursor()).isEqualTo("g9");
  }
}
