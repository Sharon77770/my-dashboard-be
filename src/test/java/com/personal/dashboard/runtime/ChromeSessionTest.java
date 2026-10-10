package com.personal.dashboard.runtime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.runtime.adapter.BrowserAdapter;
import com.personal.dashboard.runtime.service.RuntimeService;
import org.junit.jupiter.api.Test;

/** Chrome keeps profile state server-side and shares the existing session ownership boundary. */
class ChromeSessionTest {
  @Test
  void chromeEnablesAudioWithoutNavigatingAndCannotBeTakenOver() {
    var browser = mock(BrowserAdapter.class);
    var service = new RuntimeService(mock(CatalogService.class), browser, "localhost", 9223, 5901);
    var view = service.createChromeSession("first-login", 480, 800);
    var runtime = service.owned(view.id(), "first-login");
    assertThat(runtime.audioEnabled).isTrue();
    assertThat(runtime.width).isEqualTo(480);
    assertThat(runtime.height).isEqualTo(800);
    assertThat(view.url()).isEmpty();
    assertThatThrownBy(() -> service.attach(view.id(), "another-login"))
        .isInstanceOf(WorkspaceException.class);
    service.closeOwned(view.id(), "first-login");
    assertThatThrownBy(() -> service.owned(view.id(), "first-login"))
        .isInstanceOf(WorkspaceException.class);
    var auth = service.createBrowserSession(null, "Auth", "first-login", 1600, 900);
    assertThat(service.owned(auth.id(), "first-login").audioEnabled).isFalse();
    verifyNoInteractions(browser);
  }
}
