const Location = require('../models/Location');

const saveLocation = async (req, res) => {
    try {
        console.log("Location received:", req.body);
        const { userId, lat, long } = req.body;
        const loc = await Location.create({ userId, lat, long });
        res.status(201).json(loc);
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

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

module.exports = { saveLocation, getLastLocation };
