package com.ford.syncV4.syncConnection;

import android.test.InstrumentationTestCase;

import com.ford.syncV4.protocol.ProtocolFrameHeader;
import com.ford.syncV4.protocol.ProtocolFrameHeaderFactory;
import com.ford.syncV4.protocol.WiProProtocol;
import com.ford.syncV4.protocol.enums.ServiceType;
import com.ford.syncV4.session.Session;
import com.ford.syncV4.transport.SyncTransport;
import com.ford.syncV4.transport.TCPTransportConfig;
import com.ford.syncV4.transport.TransportType;
import com.ford.syncV4.util.BitConverter;

import java.util.Arrays;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Created by Andrew Batutin on 8/22/13.
 */
public class SyncConnectionTest extends InstrumentationTestCase {

    public static final byte VERSION = (byte) 2;
    public static final byte SESSION_ID = (byte) 48;
    public static final int MESSAGE_ID = 48;
    private SyncConnection sut;
    private TCPTransportConfig config;

    public SyncConnectionTest() {
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        System.setProperty("dexmaker.dexcache", getInstrumentation().getTargetContext().getCacheDir().getPath());
        config = mock(TCPTransportConfig.class);
        when(config.getTransportType()).thenReturn(TransportType.TCP);
        sut = new SyncConnection(mock(ISyncConnectionListener.class));
        sut.init(config);
        WiProProtocol protocol = (WiProProtocol) sut.getWiProProtocol();
        protocol.setVersion(VERSION);
    }

    public void testSyncConnectionShouldBeCreated() throws Exception {
        SyncConnection connection = new SyncConnection(mock(ISyncConnectionListener.class));
        connection.init(config);
        assertNotNull("should not be null", connection);
    }

    public void testStartMobileNavServiceShouldSendAppropriateBytes() throws Exception {
        byte sessionID = 0x0A;
        Session session = new Session();
        session.setSessionId(sessionID);
        ProtocolFrameHeader header = ProtocolFrameHeaderFactory.createStartSession(ServiceType.Mobile_Nav, 0x00, VERSION);
        header.setSessionID(sessionID);
        final ProtocolFrameHeader realHeader = header;
        final SyncConnection connection = new SyncConnection(mock(ISyncConnectionListener.class)) {

            @Override
            public void onProtocolMessageBytesToSend(byte[] msgBytes, int offset,
                                                     int length) {
                super.onProtocolMessageBytesToSend(msgBytes, offset, length);
                assertTrue("Arrays should be equal", Arrays.equals(msgBytes, realHeader.assembleHeaderBytes()));
                assertEquals("Offset should be 0", offset, 0);
                assertEquals("Length should be 12", length, 12);
            }
        };
        connection.init(config);
        WiProProtocol protocol = (WiProProtocol) connection.getWiProProtocol();
        protocol.setVersion(VERSION);
        connection.startMobileNavService(session);
    }

    public void testOnTransportBytesReceivedReturnedStartSessionACK() throws Exception {
        final ProtocolFrameHeader header = ProtocolFrameHeaderFactory.createStartSessionACK(ServiceType.Mobile_Nav, SESSION_ID, MESSAGE_ID, VERSION);
        final SyncConnection connection = new SyncConnection(mock(ISyncConnectionListener.class)) {

            @Override
            public void onProtocolSessionStarted(Session session, byte version, String correlationID) {
                super.onProtocolSessionStarted(session, version, correlationID);
                assertEquals("Correlation ID is empty string so far", "", correlationID);
                assertEquals("ServiceType should be equal.", header.getServiceType(), session.getServiceList().get(0).getServiceType());
                assertEquals("Frame headers should be equal.", header.getSessionID(), session.getSessionId());
                assertEquals("Version should be equal.", header.getVersion(), version);
            }
        };
        connection.init(config);
        WiProProtocol protocol = (WiProProtocol) connection.getWiProProtocol();
        protocol.setVersion(VERSION);
        connection.onTransportBytesReceived(header.assembleHeaderBytes(), header.assembleHeaderBytes().length);
    }

    public void testCloseMobileNavSessionShouldSendAppropriateBytes() throws Exception {
        byte[] data = BitConverter.intToByteArray(0);
        final ProtocolFrameHeader header = ProtocolFrameHeaderFactory.createEndSession(ServiceType.Mobile_Nav, SESSION_ID, 0, VERSION, data.length);
        final SyncConnection connection = new SyncConnection(mock(ISyncConnectionListener.class)) {

            private int count = 0;

            @Override
            public void closeMobileNavSession(byte rpcSessionID) {
                _transport = mock(SyncTransport.class);
                when(_transport.getIsConnected()).thenReturn(true);
                super.closeMobileNavSession(rpcSessionID);
            }

            @Override
            public void onProtocolMessageBytesToSend(byte[] msgBytes, int offset,
                                                     int length) {
                super.onProtocolMessageBytesToSend(msgBytes, offset, length);
                if (count == 0) {
                    assertTrue("Arrays should be equal", Arrays.equals(msgBytes, header.assembleHeaderBytes()));
                    assertEquals("Offset should be 0", offset, 0);
                    assertEquals("Length should be 12", length, 12);
                    count++;
                }
            }
        };
        connection.init(config);
        WiProProtocol protocol = (WiProProtocol) connection.getWiProProtocol();
        protocol.setVersion(VERSION);
        connection.closeMobileNavSession(SESSION_ID);
    }

    public void testStopTransportIsCalledForRPCService() throws Exception {
        SyncConnection connection = new SyncConnection(mock(ISyncConnectionListener.class)) {
            @Override
            public Boolean getIsConnected() {
                _transport = mock(SyncTransport.class);
                return super.getIsConnected();
            }
        };
        connection.init(config);
        connection.getIsConnected();
        connection.onProtocolServiceEnded(ServiceType.RPC, SESSION_ID, "");
        verify(connection._transport, times(1)).stopReading();
    }

    public void testStopTransportNotCalledForNavigationService() throws Exception {
        SyncConnection connection = new SyncConnection(mock(ISyncConnectionListener.class)) {
            @Override
            public Boolean getIsConnected() {
                _transport = mock(SyncTransport.class);
                return super.getIsConnected();
            }
        };
        connection.init(config);
        connection.getIsConnected();
        connection.onProtocolServiceEnded(ServiceType.Mobile_Nav, SESSION_ID, "");
        verify(connection._transport, never()).stopReading();

    }

    public void testStartAudioServiceShouldSendAppropriateBytes() throws Exception {
        final boolean[] isPassed = {false};
        byte sessionID = 0x0A;
        Session session = new Session();
        session.setSessionId(sessionID);
        ProtocolFrameHeader header = ProtocolFrameHeaderFactory.createStartSession(ServiceType.Audio_Service, 0x00, VERSION);
        header.setSessionID(sessionID);
        final ProtocolFrameHeader realHeader = header;
        final SyncConnection connection = new SyncConnection(mock(ISyncConnectionListener.class)) {

            @Override
            public void onProtocolMessageBytesToSend(byte[] msgBytes, int offset,
                                                     int length) {
                super.onProtocolMessageBytesToSend(msgBytes, offset, length);
                isPassed[0] = true;
                assertTrue("Arrays should be equal", Arrays.equals(msgBytes, realHeader.assembleHeaderBytes()));
                assertEquals("Offset should be 0", offset, 0);
                assertEquals("Length should be 12", length, 12);
            }
        };
        connection.init(config);
        WiProProtocol protocol = (WiProProtocol) connection.getWiProProtocol();
        protocol.setVersion(VERSION);
        connection.startAudioService(session);
        assertTrue(isPassed[0]);
    }
}