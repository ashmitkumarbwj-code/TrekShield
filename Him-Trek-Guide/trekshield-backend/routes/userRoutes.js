const express = require('express');
const router = express.Router();
const { registerUser, addContact } = require('../controllers/userController');

router.post('/register', registerUser);
router.post('/contact', addContact);

module.exports = router;
