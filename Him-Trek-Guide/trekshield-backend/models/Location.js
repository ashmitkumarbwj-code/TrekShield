const mongoose = require('mongoose');

const locationSchema = new mongoose.Schema({
    userId: { type: String, required: true }, // Changed to String from ObjectId for fast Android integration testing
    lat: { type: Number, required: true },
    long: { type: Number, required: true },
    timestamp: { type: Date, default: Date.now }
});

module.exports = mongoose.model('Location', locationSchema);
