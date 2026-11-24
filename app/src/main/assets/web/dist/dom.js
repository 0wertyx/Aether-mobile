export function el(id) {
    const element = document.getElementById(id);
    if (!element)
        throw new Error(`Missing interface element: ${id}`);
    return element;
}
export const all = (selector) => [...document.querySelectorAll(selector)];
export function notice(message) {
    const banner = el('notice');
    banner.textContent = message;
    banner.hidden = !message;
}
export function describe(error) { return error instanceof Error ? error.message : String(error); }
// The supplied PNGs include captions at the bottom. CSS crops to the glyph.
const icons = {
    home: '01_Home', dashboard: '02_Dashboard', fonts: '04_Fonts', themes: '05_Themes',
    world: '06_World_FX', indicators: '07_Indicators', simulator: '08_Simulator', workshop: '09_Workshop',
    assistant: '13_AI_Assistant', music: '14_Music', console: '17_Console', settings: '19_Settings',
    profile: '20_Profile', health: '21_Health', performance: '22_Performance', update: '23_Update',
};
export function installIcons() {
    for (const icon of all('[data-icon]')) {
        icon.style.backgroundImage = `url("assets/icons/${icons[icon.dataset.icon]}.png")`;
        icon.setAttribute('aria-hidden', 'true');
    }
}
