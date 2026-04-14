require('dotenv').config();
const express = require('express');
const cors = require('cors');
const connectDB = require('./config/db');

const userRoutes = require('./routes/userRoutes');
const locationRoutes = require('./routes/locationRoutes');

const app = express();
app.use(cors());
app.use(express.json());

connectDB();

app.use('/api/users', userRoutes);
app.use('/api/location', locationRoutes);

// Root Health Check
app.get('/', (req, res) => {
    res.status(200).send('🛡️ TrekShield API Live');
});

const PORT = process.env.PORT || 5000;
app.listen(PORT, () => {
    console.log(`🛡️ TrekShield API running on port ${PORT}`);
});

module.exports = app;
