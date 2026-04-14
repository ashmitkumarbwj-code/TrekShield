const Location    = require('../models/Location');
const TrekSession = require('../models/TrekSession');

const lastRequestMap = new Map(); // Simple in-memory rate limit (10s)
const SIXTY_MINUTES  = 60 * 60 * 1000;

// ── Heartbeat handler ──────────────────────────────────────────────────────────
const saveHeartbeat = async (req, res) => {
    try {
        const { userId, sessionId, status, battery, gps } = req.body;
        if (!userId) return res.status(400).json({ message: 'userId required' });

        // Stamp the session so silence detection has a baseline
        if (sessionId) {
            await TrekSession.findByIdAndUpdate(sessionId, {
                lastHeartbeat: new Date(),
                status: 'active'           // heartbeat proves alive → reset any unreachable flag
            });
        }
        res.status(201).json({ received: true, battery, gps });
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

// ── Location save (also used for check-in pings) ──────────────────────────────
const saveLocation = async (req, res) => {
    try {
        const { userId, lat, long, sessionId, isSOS, isFallback, type } = req.body;

        // Route heartbeat packets here too (Android sends to the same endpoint)
        if (type === 'heartbeat' || type === 'checkin') {
            return saveHeartbeat(req, res);
        }

        const now         = Date.now();
        const lastRequest = lastRequestMap.get(userId) || 0;

        // SOS bypasses rate limit; everything else is throttled at 10s
        if (!isSOS && (now - lastRequest < 10000)) {
            return res.status(429).json({ message: 'Rate limit exceeded. Try again in 10s.' });
        }
        if (!isSOS) lastRequestMap.set(userId, now);

        if (!userId || lat === undefined || long === undefined) {
            return res.status(400).json({ message: 'Missing required fields' });
        }

        const loc = await Location.create({ userId, sessionId, lat, long });

        if (sessionId) {
            const updateFields = {
                lastLocation:  { lat, long, timestamp: new Date() },
                lastHeartbeat: new Date()   // any location ping counts as heartbeat
            };
            if (isSOS) {
                updateFields.status        = 'triggered';
                updateFields.alertTriggered = true;
            }
            if (isFallback) {
                updateFields.$inc = { fallbackCount: 1 };
            }
            await TrekSession.findByIdAndUpdate(sessionId, updateFields);
        }

        res.status(201).json(loc);
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

// ── Start session ──────────────────────────────────────────────────────────────
const startSession = async (req, res) => {
    try {
        const { userId, expectedEndTime } = req.body;
        const session = await TrekSession.create({ userId, expectedEndTime });
        res.status(201).json(session);
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

// ── Last location for a user ──────────────────────────────────────────────────
const getLastLocation = async (req, res) => {
    try {
        const { userId } = req.params;
        const lastLoc = await Location.findOne({ userId }).sort({ timestamp: -1 });
        if (lastLoc) {
            res.status(200).json(lastLoc);
        } else {
            res.status(404).json({ message: 'No location history found' });
        }
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

// ── Silence detection: mark stalse sessions as "unreachable" ──────────────────
// Call this on a cron or from a status endpoint; also runs on every getSession call.
const checkAndMarkUnreachable = async (session) => {
    if (!session || session.status === 'completed') return session;
    const silenceDuration = Date.now() - new Date(session.lastHeartbeat).getTime();
    if (silenceDuration > SIXTY_MINUTES && session.status !== 'unreachable') {
        await TrekSession.findByIdAndUpdate(session._id, { status: 'unreachable' });
        session.status = 'unreachable';
    }
    return session;
};

// ── Get session (auto-applies silence detection) ──────────────────────────────
const getSession = async (req, res) => {
    try {
        let session = await TrekSession.findById(req.params.sessionId);
        if (!session) return res.status(404).json({ message: 'Session not found' });
        session = await checkAndMarkUnreachable(session);
        res.status(200).json(session);
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

module.exports = { saveLocation, saveHeartbeat, startSession, getLastLocation, getSession };
