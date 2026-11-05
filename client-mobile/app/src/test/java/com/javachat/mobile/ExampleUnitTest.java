package com.javachat.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

import com.javachat.mobile.util.InputValidator;
import com.javachat.mobile.protocol.Message;
import com.javachat.mobile.protocol.MessageType;

/**
 * Unit tests for InputValidator.
 * Run with: ./gradlew test
 */
public class ExampleUnitTest {

    @Test
    public void username_valid() {
        assertTrue(InputValidator.isValidUsername("Alice"));
    }

    @Test
    public void username_empty_invalid() {
        assertFalse(InputValidator.isValidUsername(""));
        assertFalse(InputValidator.isValidUsername(null));
    }

    @Test
    public void username_too_long_invalid() {
        assertFalse(InputValidator.isValidUsername("A".repeat(33)));
    }

    @Test
    public void port_valid() {
        assertTrue(InputValidator.isValidPort("12345"));
    }

    @Test
    public void port_out_of_range_invalid() {
        assertFalse(InputValidator.isValidPort("0"));
        assertFalse(InputValidator.isValidPort("99999"));
        assertFalse(InputValidator.isValidPort("abc"));
    }

    @Test
    public void incoming_message_null_invalid() {
        assertFalse(InputValidator.isValidIncoming(null));
    }

    @Test
    public void incoming_message_valid() {
        Message m = new Message();
        m.setType(MessageType.CHAT);
        m.setSender("Bob");
        m.setText("Hello!");
        assertTrue(InputValidator.isValidIncoming(m));
    }

    @Test
    public void sanitise_text_null() {
        assertEquals("", InputValidator.sanitiseText(null));
    }

    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }
}
