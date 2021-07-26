package org.jolokia.jvmagent.security;

import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import org.jolokia.util.AuthorizationHeaderParser;

/**
 * Simple authenticator using user and password for basic authentication.
 *
 * @author roland
 * @since 07.06.13
 */
public class UserPasswordAuthenticator extends BasicAuthenticator {

  private String user;
  private String password;

  /**
   * Authenticator which checks against a given user and password
   *
   * @param pRealm    realm for this authentication
   * @param pUser     user to check again
   * @param pPassword her password
   */
  public UserPasswordAuthenticator(String pRealm, String pUser, String pPassword) {
    super(pRealm);
    user = pUser;
    password = pPassword;
  }

  /**
   * {@inheritDoc}
   */
  public boolean checkCredentials(String pUserGiven, String pPasswordGiven) {
    return user.equals(pUserGiven) && password.equals(pPasswordGiven);
  }

  @Override
  public Result authenticate(HttpExchange httpExchange) {
    String auth = httpExchange.getRequestHeaders().getFirst("Authorization");
    if (auth == null) {//in the case where the alternate header is used
      final String alternateAuth = httpExchange.getRequestHeaders()
          .getFirst(AuthorizationHeaderParser.JOLOKIA_ALTERNATE_AUTHORIZATION_HEADER);
      if (alternateAuth != null) {
        final AuthorizationHeaderParser.Result parsed = AuthorizationHeaderParser
            .parse(alternateAuth);
        if(parsed.isValid()&&checkCredentials(parsed.getUser(), parsed.getPassword())){
          return new Success(new HttpPrincipal(parsed.getUser(), this.realm));
        }
      }
    }
    return super.authenticate(httpExchange);
  }
}
