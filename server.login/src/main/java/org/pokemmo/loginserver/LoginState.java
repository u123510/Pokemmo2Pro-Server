package org.pokemmo.loginserver;

public enum LoginState {
  UNAUTHED(-1),
  AUTHED(0),
  SYSTEM_ERROR(1),
  INVALID_PASSWORD(2),
  AUTHED_HALF(3),
  NO_GS_AVAILIBLE(6),
  ALREADY_LOGGED_IN(7),
  SERVER_DOWN(8),
  ACCOUNT_ISSUE(9),
  GM_ONLY(16),
  BAN_IP(22),
  RATE_LIMITED(23),
  ERROR_CONNECTING_AUTH_SERVER(24),
  INVALID_TOS_REVSION(25),
  BLOCKLIST_IP(26),
  ANDROID_ALPHA_PERMISSION(27),
  BLOCKLIST_IP_RANGE(28),
  ERROR_CONNECTING_GAME_SERVER(29),
  INVALID_SAVED_CREDENTIALS(30),
  ERROR_CONNECTING_FIREWALL(31),
  RATE_LIMITED_2FA(32),
  WRONG_CODE_2FA(33),
  CLIENT_OUT_OF_DATE(34),
  EXTRA_VALIDATION_FAILED(35),
  REQUIRE_QQ_FOR_ACCOUNT(36),
  ;

  private final int id;

  LoginState(int id) {
    this.id = id;
  }

  public byte getId() {
    if (id < 0) {
      return LoginState.SYSTEM_ERROR.getId();
    }
    return (byte) id;
  }
}
