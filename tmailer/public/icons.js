'use strict';
function tmIcon(name){
  const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');
  svg.setAttribute('class','icon');svg.setAttribute('viewBox','0 0 24 24');
  svg.setAttribute('aria-hidden','true');svg.setAttribute('focusable','false');
  const use=document.createElementNS('http://www.w3.org/2000/svg','use');
  use.setAttribute('href','/icons.svg?v=1.2.1#'+name);svg.append(use);return svg;
}
for(const holder of document.querySelectorAll('[data-icon]')){
  holder.setAttribute('aria-hidden','true');holder.replaceChildren(tmIcon(holder.dataset.icon));
}
