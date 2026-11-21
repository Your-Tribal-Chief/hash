package com.hash.browser.core

import org.json.JSONArray
import org.json.JSONObject

/** JavaScript snippets injected into pages. */
object Js {

    /** evaluateJavascript returns its value JSON-encoded; this unwraps a returned string. */
    fun decodeString(result: String?): String? {
        if (result == null || result == "null" || result == "undefined") return null
        return try { JSONArray("[$result]").getString(0) } catch (e: Exception) { null }
    }

    const val PRIVACY_SIGNALS =
        "try{Object.defineProperty(navigator,'globalPrivacyControl',{get:function(){return true}});" +
            "Object.defineProperty(navigator,'doNotTrack',{get:function(){return '1'}});}catch(e){}"

    fun injectCss(id: String, css: String): String {
        val q = JSONObject.quote(css)
        val qid = JSONObject.quote(id)
        return "(function(){var id=$qid;var s=document.getElementById(id);" +
            "if(!s){s=document.createElement('style');s.id=id;(document.head||document.documentElement).appendChild(s);}" +
            "s.textContent=$q;})();"
    }

    const val PAGE_SOURCE = "document.documentElement.outerHTML"

    /** Eruda is a UMD bundle; hide AMD/CommonJS globals so it always lands on window.eruda. */
    fun eruda(source: String, panel: String? = null): String {
        val show = if (panel == null) "eruda.show();" else "eruda.show(${JSONObject.quote(panel)});"
        return "(function(){if(!window.eruda){(function(define,module,exports){\n$source\n})(undefined,undefined,undefined);}" +
            "if(!window.__hashEruda){eruda.init({useShadowDom:true});window.__hashEruda=1;}$show})();"
    }

    fun erudaSelect(selector: String): String =
        "(function(){try{var n=document.querySelector(${JSONObject.quote(selector)});" +
            "if(n&&window.eruda){eruda.show('elements');eruda.get('elements').select(n);}}catch(e){}})();"

    const val READER = """(function(){
  var best=null,score=0;
  document.querySelectorAll('article,main,section,div').forEach(function(e){
    var s=0; e.querySelectorAll(':scope > p').forEach(function(p){s+=(p.innerText||'').length;});
    if(s>score){score=s;best=e;}
  });
  var out=[];
  var root=best||document.body;
  root.querySelectorAll('h1,h2,h3,p,li,blockquote').forEach(function(p){
    var t=(p.innerText||'').trim(); if(!t) return;
    var tag=p.tagName.toLowerCase();
    out.push((tag.charAt(0)=='h'?'#':'')+t);
  });
  return JSON.stringify({title:document.title,paras:out.slice(0,600)});
})()"""

    /** Tap-to-inspect: user taps an element, its details come back through prompt('hash:pick'). */
    const val PICKER = """(function(){
  if(window.__hashPick) return; window.__hashPick=1;
  var box=document.createElement('div');
  box.style.cssText='position:fixed;pointer-events:none;z-index:2147483647;border:2px solid #22D3EE;background:rgba(34,211,238,.15);border-radius:4px;transition:all .08s;left:-9px;top:-9px';
  var bar=document.createElement('div');
  bar.textContent='# Tap any element to inspect it';
  bar.style.cssText='position:fixed;top:0;left:0;right:0;z-index:2147483647;background:#0B1020;color:#22D3EE;font:600 14px sans-serif;text-align:center;padding:10px;pointer-events:none';
  document.documentElement.appendChild(box); document.documentElement.appendChild(bar);
  var ok=/^[A-Za-z_][\w-]*$/;
  function sel(e){
    var parts=[];
    while(e&&e.nodeType==1&&e!==document.documentElement&&parts.length<5){
      if(e.id&&ok.test(e.id)){parts.unshift('#'+e.id);break;}
      var s=e.tagName.toLowerCase();
      var c=Array.prototype.filter.call(e.classList||[],function(x){return ok.test(x);}).slice(0,2);
      if(c.length) s+='.'+c.join('.');
      else if(e.parentElement){s+=':nth-child('+(Array.prototype.indexOf.call(e.parentElement.children,e)+1)+')';}
      parts.unshift(s); e=e.parentElement;
    }
    return parts.join(' > ');
  }
  function pick(ev){
    ev.preventDefault(); ev.stopPropagation();
    var t=ev.target, r=t.getBoundingClientRect(), cs=getComputedStyle(t);
    box.style.left=r.left+'px'; box.style.top=r.top+'px'; box.style.width=r.width+'px'; box.style.height=r.height+'px';
    var info={selector:sel(t),tag:t.tagName.toLowerCase(),id:t.id||'',
      classes:(typeof t.className==='string')?t.className:'',
      width:Math.round(r.width),height:Math.round(r.height),display:cs.display,position:cs.position,
      color:cs.color,background:cs.backgroundColor,font:cs.fontSize+' '+cs.fontWeight+' '+cs.fontFamily,
      margin:cs.margin,padding:cs.padding,text:(t.innerText||'').slice(0,160)};
    document.removeEventListener('click',pick,true);
    setTimeout(function(){box.remove();bar.remove();window.__hashPick=0;},1500);
    prompt('hash:pick',JSON.stringify(info));
  }
  document.addEventListener('click',pick,true);
})();"""
}
