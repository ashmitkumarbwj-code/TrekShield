const mongoose = require('mongoose');

const trekSessionSchema = new mongoose.Schema({
    userId: { type: String, required: true },
    startTime: { type: Date, default: Date.now },
    expectedEndTime: { type: Date },
    status: { type: String, enum: ['active', 'completed', 'triggered', 'unreachable'], default: 'active' },
    alertTriggered:  { type: Boolean, default: false },
    fallbackCount:   { type: Number,  default: 0 },
    lastHeartbeat:   { type: Date,    default: Date.now },
    lastLocation: {
        lat: Number,
        long: Number,
        timestamp: Date
    }
});

module.exports = mongoose.model('TrekSession', trekSessionSchema);
