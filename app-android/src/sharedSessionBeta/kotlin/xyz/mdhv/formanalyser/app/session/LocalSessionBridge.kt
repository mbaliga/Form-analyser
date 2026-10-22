package xyz.mdhv.formanalyser.app.session

import dev.aarso.session.LocalSessionLedger
import dev.aarso.session.LocalSessionTransport
import dev.aarso.session.ParticipantId
import dev.aarso.session.SessionId
import dev.aarso.session.SessionPacket
import dev.aarso.session.SessionPacketListener
import dev.aarso.session.SessionPairingCode
import dev.aarso.session.SessionRole
import dev.aarso.session.TcpLocalSessionHost
import dev.aarso.session.TcpLocalSessionTransport

/** Beta transport bridge for local Fencing/Observer sessions; pairing UI remains app-owned. */
class LocalSessionBridge(
    sessionId: String,
    participantId: String,
) {
    private val ledger = LocalSessionLedger(SessionId(sessionId), ParticipantId(participantId), SessionRole.PEER)

    fun snapshot() = ledger.snapshot()
    fun accept(packet: SessionPacket) = ledger.accept(packet)
    fun appendLocalEvent(sequence: Long, eventId: String, payload: String) = ledger.localEvent(sequence, eventId, payload)
    fun connect(
        host: String,
        port: Int,
        listener: SessionPacketListener,
        pairingCode: SessionPairingCode? = null,
    ): LocalSessionTransport = TcpLocalSessionTransport.connect(host, port, listener, pairingCode)

    fun host(
        port: Int,
        listener: SessionPacketListener,
        onConnection: (LocalSessionTransport) -> Unit,
        pairingCode: SessionPairingCode? = null,
    ): TcpLocalSessionHost = TcpLocalSessionHost.bind(port, listener, onConnection, pairingCode).start()
}
