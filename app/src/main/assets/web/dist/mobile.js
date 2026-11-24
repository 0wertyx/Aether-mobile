export function installMobile(){
 document.documentElement.dataset.platform='android';
 const nav=document.getElementById('nav');const more=document.createElement('button');more.type='button';more.className='mobile-more';more.setAttribute('aria-expanded','false');more.setAttribute('aria-controls','mobile-extra');more.innerHTML='<span aria-hidden="true" class="more-glyph">•••</span><span class="navlabel">More</span>';
 const extra=document.createElement('div');extra.id='mobile-extra';extra.className='mobile-extra';extra.hidden=true;
 const secondary=['indicators','scripts','developer'];
 for(const key of secondary){const button=nav.querySelector(`[data-tab=${key}]`);const copy=button.cloneNode(true);copy.classList.add('mobile-extra-button');extra.append(copy);button.classList.add('wide-only');}
 nav.append(more,extra);
 function close(){extra.hidden=true;more.setAttribute('aria-expanded','false');}
 more.onclick=()=>{extra.hidden=!extra.hidden;more.setAttribute('aria-expanded',String(!extra.hidden));};
 extra.addEventListener('click',close);document.addEventListener('pointerdown',e=>{if(!nav.contains(e.target))close();});
 const observer=new MutationObserver(()=>more.classList.toggle('active',secondary.some(key=>nav.querySelector(`[data-tab=${key}]`).classList.contains('active'))));for(const key of secondary)observer.observe(nav.querySelector(`[data-tab=${key}]`),{attributes:true,attributeFilter:['class']});
 window.aetherBack=()=>{if(!extra.hidden){close();return true;}const active=nav.querySelector('.active[data-tab]');if(active?.dataset.tab!=='dashboard'){nav.querySelector('[data-tab=dashboard]').click();return true;}return false;};
 const disable=(sel,reason)=>{const n=document.querySelector(sel);if(n){n.disabled=true;n.title=reason;}};
 disable('#output-mode option[value=overlay]','External HUD is not included');disable('#source-mode option[value=studio]','Studio relay is not included');disable('#pair-place','Local simulation only');
 for(const key of ['overlayOpacity','overlayTopmost','monitor'])document.querySelector(`[data-setting=${key}]`)?.closest('label,.field')?.setAttribute('hidden','');
 document.querySelector('#edit-overlay')?.closest('label')?.setAttribute('hidden','');
 document.querySelector('#diag-dotnet')?.parentElement?.firstChild && (document.querySelector('#diag-dotnet').parentElement.firstChild.textContent='Native runtime ');
}
