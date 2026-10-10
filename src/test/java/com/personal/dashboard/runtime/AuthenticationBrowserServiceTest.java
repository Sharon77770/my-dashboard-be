package com.personal.dashboard.runtime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.personal.dashboard.catalog.entity.ApplicationRecord;
import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.runtime.adapter.BrowserAdapter;
import com.personal.dashboard.runtime.dto.AuthenticationBrowserRequest;
import com.personal.dashboard.runtime.service.AuthenticationBrowserService;
import com.personal.dashboard.runtime.service.RuntimeService;
import org.junit.jupiter.api.Test;

/** Tests trusted destination selection and login ownership without using real provider accounts. */
class AuthenticationBrowserServiceTest {
  private final CatalogService catalog = mock(CatalogService.class);
  private final BrowserAdapter browser = mock(BrowserAdapter.class);
  private final RuntimeService runtimes =
      new RuntimeService(catalog, browser, "browser", 9223, 5901);
  private final AuthenticationBrowserService service =
      new AuthenticationBrowserService(catalog, runtimes);

  private AuthenticationBrowserRequest request(String provider, String url, String app) {
    return new AuthenticationBrowserRequest(provider, url, app, 1600, 900);
  }

  @Test
  void providerPagesAlwaysUsePersistentServerAndSessionOwnedHandles() {
    for (String provider : new String[] {"GOOGLE", "GITHUB", "CODEX"}) {
      var view = service.open(request(provider, null, null), "login-one");
      var runtime = runtimes.owned(view.id(), "login-one");
      assertThat(runtime.device.host()).isEqualTo("browser");
      assertThat(runtime.device.remotePort()).isEqualTo(5901);
      assertThat(view.url()).isEmpty();
      assertThatThrownBy(() -> runtimes.owned(view.id(), "login-two"))
          .isInstanceOf(WorkspaceException.class);
      runtimes.closeOwned(view.id(), "login-one");
    }
    verify(browser).open("browser", 9223, "https://accounts.google.com/");
    verify(browser).open("browser", 9223, "https://github.com/login");
    verify(browser).open("browser", 9223, "https://chatgpt.com/auth/login");
    verifyNoInteractions(catalog);
  }

  @Test
  void deviceFlowLinksAndRegisteredAppsUseTheSameBrowser() {
    service.open(request("CODEX", "https://auth.openai.com/codex/device", null), "owner");
    service.open(request("GITHUB", "https://github.com/login/device", null), "owner");
    when(catalog.requireApplication("app-one"))
        .thenReturn(new ApplicationRecord("app-one", "App", "https://example.test/app", true));
    service.open(request("APP", null, "app-one"), "owner");
    verify(browser).open("browser", 9223, "https://auth.openai.com/codex/device");
    verify(browser).open("browser", 9223, "https://github.com/login/device");
    verify(browser).open("browser", 9223, "https://example.test/app");
  }

  @Test
  void browserReconnectDoesNotCreateAnotherProviderTabAndCapacityPrecedesNavigation() {
    for (int index = 0; index < 12; index++) service.open(request("BROWSER", null, null), "owner");
    assertThatThrownBy(() -> service.open(request("GOOGLE", null, null), "owner"))
        .isInstanceOf(WorkspaceException.class)
        .extracting(error -> ((WorkspaceException) error).status())
        .isEqualTo(409);
    verifyNoInteractions(browser);
  }

  @Test
  void invalidDestinationsNeverReachTheBrowserOrLeakInputInErrors() {
    for (String url :
        new String[] {
          "https://github.com.evil.test/login",
          "http://github.com/login",
          "https://github.com:8443/login",
          "https://secret@github.com/login",
          "javascript:alert(1)",
          "https://127.0.0.1/",
          "https://accounts.google.com/",
          "https://github.com\\@evil.test/"
        }) {
      assertThatThrownBy(() -> service.open(request("GITHUB", url, null), "owner"))
          .isInstanceOf(WorkspaceException.class)
          .hasMessageNotContaining(url);
    }
    assertThatThrownBy(() -> service.open(request("BROWSER", "https://github.com/", null), "owner"))
        .isInstanceOf(WorkspaceException.class);
    assertThatThrownBy(() -> service.open(request("APP", "https://github.com/", "app"), "owner"))
        .isInstanceOf(WorkspaceException.class);
    verifyNoInteractions(browser, catalog);
  }
}
