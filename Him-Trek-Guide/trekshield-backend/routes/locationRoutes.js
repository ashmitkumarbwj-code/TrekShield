const express = require('express');
const { saveLocation, saveHeartbeat, startSession, getLastLocation, getSession } = require('../controllers/locationController');
const router = express.Router();

router.post('/save',          saveLocation);
router.post('/heartbeat',     saveHeartbeat);   // explicit heartbeat endpoint
router.post('/start-session', startSession);
router.get('/session/:sessionId', getSession);  // includes silence detection
router.get('/:userId',        getLastLocation);

module.exports = router;
