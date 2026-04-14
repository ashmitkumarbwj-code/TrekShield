const mongoose = require('mongoose');

const locationSchema = new mongoose.Schema({
    userId: { type: String, required: true },
    sessionId: { type: String }, // Links to a specific TrekSession
    lat: { type: Number, required: true },
    long: { type: Number, required: true },
    timestamp: { type: Date, default: Date.now }
});

module.exports = mongoose.model('Location', locationSchema);
