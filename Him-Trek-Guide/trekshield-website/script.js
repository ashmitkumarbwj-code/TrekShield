document.addEventListener("DOMContentLoaded", () => {
    // Add subtle paralyx effect to the phone mockup on mouse move
    const heroImage = document.querySelector('.glass-panel');
    
    if (heroImage && window.innerWidth > 900) {
        document.addEventListener('mousemove', (e) => {
            const xAxis = (window.innerWidth / 2 - e.pageX) / 50;
            const yAxis = (window.innerHeight / 2 - e.pageY) / 50;
            
            heroImage.style.transform = `perspective(1000px) rotateY(${-15 + xAxis}deg) rotateX(${yAxis}deg)`;
        });

        // Reset transform on mouse leave
        document.addEventListener('mouseleave', () => {
            heroImage.style.transform = `perspective(1000px) rotateY(-15deg)`;
        });
    }

    // Smooth scroll for anchor links
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function (e) {
            e.preventDefault();
            const target = document.querySelector(this.getAttribute('href'));
            if(target) {
                target.scrollIntoView({
                    behavior: 'smooth'
                });
            }
        });
    });
});
