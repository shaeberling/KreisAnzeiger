/*
 * Copyright 2011 Sascha HŠberling
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package de.haeberling.kapub.standalone;

import java.util.Properties;
import java.util.logging.Logger;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * Sends out e-mails asynchronously.
 */
public class Mailing extends Thread {
  private static final Logger log = Logger.getLogger(Mailing.class.getName());

  // NOTE: Set the values to enable mail functionality.
  private static final String SMTP_SERVER = "";
  private static final String SMTP_EMAIL_ADDRESS = "";
  private static final String SMTP_USERNAME = "";
  private static final String SMTP_PASSWORD = "";
  private static final String EMAIL_RECIPIENT = "";

  private final String subject;
  private final String text;

  /** Asynchronously sends out an e-mail. */
  public static void sendMail(String subject, String text) {
    (new Mailing(subject, text)).start();
  }

  private Mailing(String subject, String text) {
    this.subject = subject;
    this.text = text;
  }

  @Override
  public void run() {
    Properties props = new Properties();
    props.put("mail.smtp.host", SMTP_SERVER);
    props.put("mail.smtp.auth", "true");

    Session session = Session.getDefaultInstance(props,
        new javax.mail.Authenticator() {
          @Override
          protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(SMTP_USERNAME, SMTP_PASSWORD);
          }
        });

    try {
      Message message = new MimeMessage(session);
      message.setFrom(new InternetAddress(SMTP_EMAIL_ADDRESS));
      message.setRecipients(Message.RecipientType.TO,
          InternetAddress.parse(EMAIL_RECIPIENT));
      message.setSubject(subject);
      message.setText(text);
      Transport.send(message);
      log.info("Mail sent.");
    } catch (MessagingException e) {
      log.severe("Could not send e-mail: " + e.getMessage());
    }
  }
}
