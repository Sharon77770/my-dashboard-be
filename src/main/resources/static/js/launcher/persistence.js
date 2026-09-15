'use strict';
/** Browser-scoped presentation persistence. Account is supplied by the authenticated Thymeleaf page. */
window.HomePersistence = {
  key(){return `workspace-home-v1:${encodeURIComponent(document.body.dataset.account || 'owner')}`;},
  load(){const text=localStorage.getItem(this.key());return text?JSON.parse(text):null;},
  save(layout){localStorage.setItem(this.key(),JSON.stringify(layout));}
};
