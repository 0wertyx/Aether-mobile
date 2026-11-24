export function installNativeUpdates() {
    const bridge = window.AetherAndroid;
    if (!bridge || typeof bridge.checkForUpdates !== 'function') return;
    const developer = document.getElementById('developer');
    if (!developer || document.getElementById('aether-native-update')) return;
    const panel = document.createElement('article');
    panel.className = 'panel';
    panel.id = 'aether-native-update';
    const heading = document.createElement('h3');
    heading.textContent = 'Application updates';
    const description = document.createElement('p');
    description.className = 'muted';
    description.textContent = 'Stable updates are checked automatically when AETHER opens.';
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'btn primary';
    button.textContent = 'Check for updates';
    button.addEventListener('click', () => bridge.checkForUpdates());
    panel.append(heading, description, button);
    developer.append(panel);
}
