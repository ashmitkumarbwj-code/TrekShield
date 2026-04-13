const User = require('../models/User');

const registerUser = async (req, res) => {
    try {
        const { name, phone } = req.body;
        // Basic check
        let user = await User.findOne({ phone });
        if (!user) {
            user = await User.create({ name, phone });
        }
        res.status(201).json(user);
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

const addContact = async (req, res) => {
    try {
        const { userId, contactNumber } = req.body;
        const user = await User.findById(userId);
        if (user) {
            user.emergencyContacts.push(contactNumber);
            await user.save();
            res.status(200).json(user);
        } else {
            res.status(404).json({ message: 'User not found' });
        }
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

module.exports = { registerUser, addContact };
