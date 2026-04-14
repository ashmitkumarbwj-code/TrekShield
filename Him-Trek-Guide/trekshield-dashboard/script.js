const map = L.map('map').setView([31.1048, 77.1734], 13); // Default Shimla

L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 19,
  attribution: '© OpenStreetMap'
}).addTo(map);

// PRODUCTION URL - We'll update this once deployment is confirmed
const API_URL = "https://trekshield-backend.onrender.com/api/location/last/";

let marker = null;
const statusIndicator = document.getElementById("status-indicator");

// User ID prompt for live dynamic tracking
const userId = prompt("Enter the User ID to track:", "YOUR_USER_ID");

async function fetchLocation() {
  if (!userId || userId === "YOUR_USER_ID") {
    statusIndicator.innerText = "No User ID entered. Standing by.";
    return;
  }

  statusIndicator.innerText = `Fetching signal for ${userId}...`;

  try {
    const res = await fetch(API_URL + userId);
    
    if (!res.ok) {
        statusIndicator.innerText = "No active trek signal found.";
        return;
    }

    const data = await res.json();

    if (data && data.lat) {
      if (marker) {
          marker.setLatLng([data.lat, data.long]);
      } else {
          marker = L.marker([data.lat, data.long]).addTo(map);
          marker.bindPopup("Last Known Location").openPopup();
      }
      map.setView([data.lat, data.long], 16);
      
      const timeStr = new Date().toLocaleTimeString();
      statusIndicator.innerText = `Connected. Last sync: ${timeStr}`;
      statusIndicator.style.backgroundColor = "rgba(46, 204, 113, 0.2)";
      statusIndicator.style.borderColor = "#2ECC71";
    }
  } catch (err) {
    console.error(err);
    statusIndicator.innerText = "Connection to Vercel failed.";
    statusIndicator.style.backgroundColor = "rgba(231, 76, 60, 0.2)";
    statusIndicator.style.borderColor = "#E74C3C";
  }
}

// Polling interval: 30 seconds for battery-conscious backend performance
setInterval(fetchLocation, 30000);

fetchLocation();
