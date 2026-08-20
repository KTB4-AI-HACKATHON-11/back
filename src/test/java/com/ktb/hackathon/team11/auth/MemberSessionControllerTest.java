package com.ktb.hackathon.team11.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;

@SpringBootTest(properties = "auth.cookie-secure=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberSessionControllerTest {
  @Autowired private MockMvc mockMvc;

  @Test
  void nicknameSignupIssuesSessionForMeAndLogoutRevokesIt() throws Exception {
    MvcResult signup =
        mockMvc
            .perform(
                post("/api/v1/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nickname\":\"세션테스트매니저\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.nickname").value("세션테스트매니저"))
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Secure")))
            .andReturn();

    Cookie session = signup.getResponse().getCookie(SessionService.COOKIE_NAME);
    mockMvc
        .perform(get("/api/v1/members/me").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.nickname").value("세션테스트매니저"));

    mockMvc.perform(post("/api/v1/members/logout").cookie(session)).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/members/me").cookie(session)).andExpect(status().isUnauthorized());
  }
}
