const express = require('express');
const router = express.Router();
const { saveLocation, getLastLocation } = require('../controllers/locationController');

router.post('/save', saveLocation);
router.get('/last/:userId', getLastLocation);

module.exports = router;
