'use strict';
// Independent genuine 13-GLB Android-like Chromium mobile-touch proof.
// Use real touchscreen taps rather than Playwright mouse clicks for phone UI.
// V42: capture exact redo hit-test/touch-dispatch evidence without force or relaxed gates.
// Isolated room editor only; no Android APK/emulator/app integration.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {chromium}=require('playwright');
const {performance:nodePerformance,monitorEventLoopDelay}=require('node:perf_hooks');
const os=require('node:os');
const zlib=require('node:zlib');
const repo=path.resolve(__dirname,'../..');
const out=process.env.ROOM_EDITOR_EVIDENCE_DIR || '/tmp/celine-room-editor-mobile-touch';
// Native AA4 is enabled ONLY by in-memory, localhost proof routing. The
// production editor.js, 13 GLBs, source JSON and camera remain byte-identical.
const NATIVE_AA_FLAG=process.env.ROOM_EDITOR_NATIVE_MSAA4||'0';
assert(['0','1'].includes(NATIVE_AA_FLAG),'Native mobile AA mode must be 0 or 1');
const NATIVE_AA4_MODE=NATIVE_AA_FLAG==='1';
// V150 GPU/Viz SwapBuffers wait: test-process-only VSync hypothesis.
// Never modify editor runtime, the user source, or official touch thresholds.
const GPU_VSYNC_PROBE=process.env.ROOM_EDITOR_GPU_VSYNC_PROBE||'default';
assert(['default','no-vsync'].includes(GPU_VSYNC_PROBE),
  'Only default or no-vsync GPU timing diagnostic permitted');
fs.mkdirSync(out,{recursive:true});
const record=[], errors=[];

// Proof-only target identity. Do not attribute a preceding tap's trailing
// pointer events to a subsequent button; never substitute an unmatched event.
function expectedBrowserTouchTarget(label){
  const s=String(label);
  if(s.includes('#mobile-tools-panel > summary'))
    return {target:'SUMMARY',scope:'mobile-tools-panel'};
  if(s.includes('.mobile-extra > summary'))
    return {target:'SUMMARY',scope:'mobile-extra'};
  if(s==='wallButton')return {target:'toggle-walls'};
  if(s==='s25Button')return {target:'mobile-s25-shortcut'};
  if(s==='neutralButton')return {target:'mobile-neutral-shortcut'};
  for(const [field] of [['data-mobile-action'],['data-mobile-zoom'],['data-nudge']]){
    const marker=field+'="';
    const start=s.indexOf(marker);
    if(start>=0){
      const end=s.indexOf('"',start+marker.length);
      if(end>start)return {target:s.slice(start+marker.length,end)};
    }
  }
  const id=s.match(/#(mobile-[a-z0-9-]+)/);
  return id?{target:id[1]}:null;
}

function summarizeTouchAttribution(browserTrace,taps){
  return taps.map(tap=>{
      // The previous +/-100ms window incorrectly selected stale events from
      // neighboring taps (observed zoom-in -4.4ms). Attribute ONLY trusted
      // events delivered to the expected target inside this tap's own clock
      // interval; unmapped targets must remain UNATTRIBUTED, not guessed.
      const expected=expectedBrowserTouchTarget(tap.label);
      const nearby=(browserTrace?.events||[]).filter(event=>
        event.epoch_ms>=tap.start_epoch_ms&&
        event.epoch_ms<=tap.end_epoch_ms);
      const events=expected?nearby.filter(event=>
        event.trusted&&event.target===expected.target&&
        (!expected.scope||event.scope===expected.scope)):[];
      const first=type=>events.find(event=>event.type===type)?.epoch_ms??null;
      const pointer=first('pointerdown'),click=first('click');
      const gaps=(browserTrace?.raf_gaps||[]).filter(gap=>
        gap.epoch_ms>=tap.start_epoch_ms-150&&gap.epoch_ms<=tap.end_epoch_ms+150);
      const tasks=(browserTrace?.longtasks||[]).filter(task=>
        task.epoch_ms>=tap.start_epoch_ms-150&&task.epoch_ms<=tap.end_epoch_ms+150);
      return {...tap,
        expected_event_target:expected,
        target_attribution:expected?
          (pointer!==null?'TRUSTED_EXPECTED_POINTER':'EXPECTED_POINTER_NOT_OBSERVED'):
          'UNMAPPED_TARGET_NO_ATTRIBUTION',
        ignored_nearby_unmatched_events:nearby.length-events.length,
        touchstart_after_start_ms:first('touchstart')===null?null:first('touchstart')-tap.start_epoch_ms,
        pointerdown_after_start_ms:pointer===null?null:pointer-tap.start_epoch_ms,
        click_after_start_ms:click===null?null:click-tap.start_epoch_ms,
        pre_target_pointerdown_ms:pointer===null?null:pointer-tap.start_epoch_ms,
        post_target_pointerdown_to_locator_return_ms:pointer===null?null:tap.end_epoch_ms-pointer,
        real_browser_events:events,
        max_raf_gap_ms:gaps.length?Math.max(...gaps.map(g=>g.gap_ms)):0,
        max_longtask_ms:tasks.length?Math.max(...tasks.map(t=>t.duration_ms)):0,
        longtasks_near_tap:tasks.length
      };
  });
}

const observedMobileTaps=[];
let activeProofContext=null,activeProofPage=null,realTapProbeSequence=0;
// V146 read-only differential: V145's paused first canvas touch was FAST in
// both modes, while later authentic AA4 Playwright taps still took 5-14s.
// Time a second independent renderer CDP session DURING those existing taps,
// never dispatch an additional input event or widen any strict latency gate.
function passiveProbeForExistingTap(label) {
  if(!NATIVE_AA4_MODE||!activeProofContext||!activeProofPage)return null;
  const s=String(label);
  const name=s==='neutralButton'?'neutral':
    s==='s25Button'?'s25':
    s.includes('[data-nudge="x:1"]')?'xplus':
    s.includes('[data-mobile-zoom="0.82"]')?'zoomout':
    s.includes('[data-mobile-zoom="1.22"]')?'zoomin':
    s.includes('[data-mobile-action="save"]')?'save':
    s.includes('#mobile-tools-panel > summary')?'tools':null;
  return name?'aa4-real-'+name+'-'+(++realTapProbeSequence):null;
}
async function observeRealTap(locator,label) {
  const probeLabel=passiveProbeForExistingTap(label);
  let passiveProbe=null;
  if(probeLabel) {
    try {
      passiveProbe=await beginPassiveRendererCdpProbe(
        activeProofContext,activeProofPage,probeLabel);
    } catch(error) {
      record.push({name:'AA4_REAL_TAP_PASSIVE_CDP_INSTALL_UNAVAILABLE',
        label,diagnostic_error:String(error),visual_acceptance:'NOT_GRANTED'});
    }
  }
  const began=Date.now(); let completed=false;
  try {
    const value=await locator.tap();
    completed=true;
    return value;
  } finally {
    const ended=Date.now();
    observedMobileTaps.push({
      label,start_epoch_ms:began,end_epoch_ms:ended,
      elapsed_ms:ended-began,action_succeeded:completed
    });
    if(passiveProbe) {
      try {
        const summary=await passiveProbe.stop();
        record.push({name:'AA4_REAL_EXISTING_TOUCH_PASSIVE_CDP_DIAGNOSTIC',
          label,start_epoch_ms:began,end_epoch_ms:ended,
          real_touch_elapsed_ms:ended-began,...summary,
          visual_acceptance:'NOT_GRANTED'});
      } catch(error) {
        record.push({name:'AA4_REAL_TAP_PASSIVE_CDP_READBACK_UNAVAILABLE',
          label,diagnostic_error:String(error),visual_acceptance:'NOT_GRANTED'});
      }
    }
  }
}

// Proof-only V140: sample genuine AA4 X+ nudge hit testing while the
// EXISTING Playwright locator.tap proceeds unmodified. This distinguishes the
// observed Save-over-nudge interception/scroll from unexplained stable waits.
// It does not create synthetic events, modify layout, loosen <5000ms gates,
// or claim an Android/Samsung visual PASS.
async function observeNudgeTapWithHitTest(page,locator,label){
  if(!NATIVE_AA4_MODE)return observeRealTap(locator,label);
  const selector='[data-nudge="x:1"]';
  const installation=await page.evaluate(selector=>{
    const target=document.querySelector(selector);
    if(!target)throw new Error('Missing authentic X+ nudge target');
    const data={samples:[],events:[],raf_gaps:[],frame_count:0};
    const epoch=()=>performance.timeOrigin+performance.now();
    const rectangle=element=>{
      if(!element)return null;
      const b=element.getBoundingClientRect();
      return [b.left,b.top,b.width,b.height];
    };
    const identify=element=>element?{
      tag:element.tagName,id:element.id||null,
      mobile_action:element.getAttribute('data-mobile-action'),
      nudge:element.getAttribute('data-nudge'),
      class_name:typeof element.className==='string'?element.className.slice(0,90):null
    }:null;
    const sample=source=>{
      const b=target.getBoundingClientRect();
      const x=b.left+b.width/2,y=b.top+b.height/2;
      const centerInside=x>=0&&x<innerWidth&&y>=0&&y<innerHeight;
      const stack=centerInside?document.elementsFromPoint(x,y):[];
      const save=document.querySelector('[data-mobile-action="save"]');
      const s=save?.getBoundingClientRect();
      const intersection=s?Math.max(0,Math.min(b.right,s.right)-Math.max(b.left,s.left))*
        Math.max(0,Math.min(b.bottom,s.bottom)-Math.max(b.top,s.top)):0;
      const style=getComputedStyle(target);
      data.samples.push({
        epoch_ms:epoch(),source,
        target_rect:rectangle(target),save_rect:rectangle(save),
        target_center:[x,y],center_inside_viewport:centerInside,
        hit_is_nudge:!!stack[0]&&(stack[0]===target||target.contains(stack[0])),
        hit_stack:stack.slice(0,5).map(identify),
        target_save_rect_overlap_css_px2:intersection,
        target_connected:target.isConnected,target_disabled:target.disabled,
        target_display:style.display,target_visibility:style.visibility,
        target_pointer_events:style.pointerEvents,
        scroll_y:window.scrollY,
        viewport_css:[innerWidth,innerHeight],
        visual_viewport_offset_y:visualViewport?.offsetTop??null
      });
      if(data.samples.length>250)data.samples.shift();
    };
    const listener=e=>{
      if(!(e.target===target||target.contains(e.target)))return;
      data.events.push({
        type:e.type,trusted:e.isTrusted,epoch_ms:epoch(),
        target:identify(e.target)
      });
    };
    for(const type of ['pointerdown','touchstart'])
      document.addEventListener(type,listener,{capture:true,passive:true});
    let lastFrame=0,rafId=0;
    const frame=t=>{
      data.frame_count++;
      if(lastFrame&&t-lastFrame>100)
        data.raf_gaps.push({epoch_ms:performance.timeOrigin+t,gap_ms:t-lastFrame});
      lastFrame=t;
      rafId=requestAnimationFrame(frame);
    };
    sample('start');
    rafId=requestAnimationFrame(frame);
    const interval=setInterval(()=>sample('interval_180ms'),180);
    window.__celineXPlusHitTestStop=()=>{
      clearInterval(interval);cancelAnimationFrame(rafId);
      for(const type of ['pointerdown','touchstart'])
        document.removeEventListener(type,listener,true);
      sample('stop');
      delete window.__celineXPlusHitTestStop;
      return data;
    };
    return {installed:true,first_sample:data.samples[0]};
  },selector);
  const started=Date.now();let tapError=null;
  try{
    return await observeRealTap(locator,label);
  }catch(error){
    tapError=String(error.stack||error);
    throw error;
  }finally{
    const finished=Date.now();
    const measurement=await page.evaluate(()=>
      window.__celineXPlusHitTestStop?.()||{missing_stop:true}
    ).catch(error=>({read_error:String(error)}));
    const evidence={
      mode:'genuine_native_aa4_emulation',
      source_sha:process.env.GITHUB_SHA||null,
      label,selector,installation,
      playwright_tap_start_epoch_ms:started,
      playwright_tap_finish_epoch_ms:finished,
      playwright_tap_elapsed_ms:finished-started,
      tap_error:tapError,measurement,
      debug_log:'GitHub mobile job DEBUG=pw:api',
      strict_lt_5000ms_gate:'UNCHANGED',
      warning:'Hit testing at the DOM center is a sampled geometry proxy; Playwright may use a different visible click point. Does not establish browser/compositor/driver root cause.',
      visual_acceptance:'NOT_GRANTED'
    };
    fs.writeFileSync(path.join(out,'mobile-aa4-xplus-hit-test.json'),
      JSON.stringify(evidence,null,2));
    record.push({name:'AA4_REAL_XPLUS_HIT_TEST_DIAGNOSTIC_ONLY',
      file:'mobile-aa4-xplus-hit-test.json',
      samples:measurement.samples?.length||0,
      intercepted_samples:measurement.samples?.filter(s=>
        s.center_inside_viewport&&!s.hit_is_nudge).length||0,
      playwright_tap_ms:finished-started,visual_acceptance:'NOT_GRANTED'});
  }
}

async function traceOriginalNeutralRestoreActionability(page,neutralButton,phase='restore',selector='[data-mobile-action="neutral-shell"]',label='neutralButton') {
  // Proof-only, low-rate geometry/event tracing while the authentic locator.tap
  // waits. No clicks, forced taps, target movement or threshold modifications.
  const installStarted=Date.now();
  const installation=await page.evaluate(selector=>{
    const button=document.querySelector(selector);
    if(!button)throw new Error('Missing genuine neutral-shell viewport shortcut');
    const state={
      started_epoch_ms:performance.timeOrigin+performance.now(),
      samples:[],events:[],mutations:[],raf_gaps:[],frames:0,
      raf_timestamps_epoch_ms:[],raf_last_epoch_ms:null
    };
    const sample=source=>{
      const r=button.getBoundingClientRect(),style=getComputedStyle(button);
      const x=r.x+r.width/2,y=r.y+r.height/2;
      const hit=document.elementFromPoint(x,y);
      const item={
        epoch_ms:performance.timeOrigin+performance.now(),source,
        rect:[r.x,r.y,r.width,r.height],
        center:[x,y],
        hit_tag:hit?.tagName||null,hit_id:hit?.id||null,
        hit_button:hit===button||button.contains(hit),
        connected:button.isConnected,disabled:button.disabled,
        display:style.display,visibility:style.visibility,
        opacity:style.opacity,pointer_events:style.pointerEvents,
        aria_pressed:button.getAttribute('aria-pressed'),
        text:button.textContent.trim().slice(0,90),
        scroll_y:window.scrollY,
        visual_viewport_offset_top:window.visualViewport?.offsetTop??null
      };
      state.samples.push(item);
      if(state.samples.length>300)state.samples.shift();
    };
    const onEvent=event=>{
      if(!(event.target===button||button.contains(event.target)))return;
      state.events.push({
        type:event.type,trusted:event.isTrusted,
        event_original_epoch_ms:performance.timeOrigin+event.timeStamp,
        listener_epoch_ms:performance.timeOrigin+performance.now(),
        pointer_type:event.pointerType||null,
        target_id:event.target?.id||null
      });
    };
    for(const type of ['pointerdown','touchstart','pointerup','touchend'])
      document.addEventListener(type,onEvent,{capture:true,passive:true});
    const observer=new MutationObserver(mutations=>{
      for(const mutation of mutations)state.mutations.push({
        epoch_ms:performance.timeOrigin+performance.now(),
        type:mutation.type,attribute:mutation.attributeName||null
      });
    });
    observer.observe(button,{attributes:true,childList:true,subtree:true});
    let rafHandle=0,lastRaf=0;
    const tick=t=>{
      state.frames++;
      const frameEpoch=performance.timeOrigin+t;
      state.raf_last_epoch_ms=frameEpoch;
      if(state.raf_timestamps_epoch_ms.length<180)
        state.raf_timestamps_epoch_ms.push(frameEpoch);
      if(lastRaf && t-lastRaf>100)state.raf_gaps.push({
        epoch_ms:frameEpoch,gap_ms:t-lastRaf
      });
      lastRaf=t;
      if(state.frames%6===0)sample('raf_every_sixth_frame');
      rafHandle=requestAnimationFrame(tick);
    };
    sample('start');
    rafHandle=requestAnimationFrame(tick);
    const interval=setInterval(()=>sample('interval_180ms'),180);
    window.__celineNeutralRestoreActionabilityStop=()=>{
      clearInterval(interval);cancelAnimationFrame(rafHandle);
      observer.disconnect();
      for(const type of ['pointerdown','touchstart','pointerup','touchend'])
        document.removeEventListener(type,onEvent,true);
      sample('stop');
      delete window.__celineNeutralRestoreActionabilityStop;
      const stoppedEpoch=performance.timeOrigin+performance.now();
      // No completed rAF gap is emitted when the next callback never arrives;
      // retain the still-open frame interval at genuine tap completion.
      const lastFrameEpoch=state.raf_last_epoch_ms;
      return {
        started_epoch_ms:state.started_epoch_ms,
        stopped_epoch_ms:stoppedEpoch,
        samples:state.samples,events:state.events,
        mutations:state.mutations,raf_gaps:state.raf_gaps,
        raf_timestamps_epoch_ms:state.raf_timestamps_epoch_ms,
        raf_first_epoch_ms:state.raf_timestamps_epoch_ms[0]??null,
        raf_last_epoch_ms:lastFrameEpoch,
        raf_pending_gap_at_stop_ms:stoppedEpoch-(lastFrameEpoch??state.started_epoch_ms),
        frame_count:state.frames
      };
    };
    return {installed:true,starting_sample:state.samples[0]};
  },selector);
  // V149: trace the SAME genuine AA4 neutral OR S25-display actionability
  // interval: V148 real S25 touch 5133ms failed before trusted pointerdown.
  // Node 100ms heartbeat and Chromium cc/viz/BeginFrame are passive; they
  // never synthesize input, alter the UI, or relax the strict <5000ms gate.
  let neutralSchedulingSession=null,neutralScheduling=null;
  if(NATIVE_AA4_MODE && (phase==='original'||phase==='s25-enter')) {
    try {
      neutralSchedulingSession=await page.context().newCDPSession(page);
      neutralScheduling=await beginFocusedSchedulingTrace(
        neutralSchedulingSession,phase==='s25-enter'?'aa4-real-s25-enter':'aa4-real-neutral-original',
        'toplevel,latency,cc,viz,benchmark,blink.user_timing,gpu,toplevel.flow,disabled-by-default-devtools.timeline.frame');
      await page.evaluate(marker=>performance.mark(marker),
        phase==='s25-enter'?'CELINE_E2E_AA4_REAL_S25_BEFORE':'CELINE_E2E_AA4_REAL_NEUTRAL_BEFORE');
    }catch(error){
      record.push({name:phase==='s25-enter'?'AA4_REAL_S25_SCHEDULING_INSTALL_UNAVAILABLE':'AA4_REAL_NEUTRAL_SCHEDULING_INSTALL_UNAVAILABLE',
        diagnostic_error:String(error),visual_acceptance:'NOT_GRANTED'});
    }
  }
  let tapElapsed=null,tapError=null;
  const tapStarted=Date.now();
  try {
    await observeRealTap(neutralButton,label);
    tapElapsed=Date.now()-tapStarted;
  } catch(e) {
    tapElapsed=Date.now()-tapStarted;
    tapError=String(e.stack||e);
    throw e;
  } finally {
    const tapFinished=Date.now();
    if(neutralScheduling)await page.evaluate(marker=>performance.mark(marker),
      phase==='s25-enter'?'CELINE_E2E_AA4_REAL_S25_AFTER':'CELINE_E2E_AA4_REAL_NEUTRAL_AFTER').catch(()=>{});
    const measurement=await page.evaluate(()=>
      window.__celineNeutralRestoreActionabilityStop?.()||{not_installed:true}
    ).catch(e=>({trace_read_error:String(e)}));
    const evidence={
      mode:NATIVE_AA4_MODE?'native_aa4_proof_only':'native_aa0',
      source_sha:process.env.GITHUB_SHA||null,
      control:phase==='s25-enter'?'real S25 display mobile shortcut, same 13-source-GLB scene':'real neutral-shell mobile shortcut, same 13-source-GLB scene',
      install_elapsed_ms:tapStarted-installStarted,
      installation,
      playwright_tap_start_epoch_ms:tapStarted,
      playwright_tap_finish_epoch_ms:tapFinished,
      playwright_tap_elapsed_ms:tapElapsed,
      playwright_error:tapError,
      measurement,
      strict_threshold_ms_exclusive:5000,
      caveat:'Underlying Playwright locator.tap CDP ACK is not separately exposed; compare with independent native CDP target probe. rAF/rect measurements do not prove compositor timing.'
    };
    const filename=phase==='s25-enter'?'mobile-s25-enter-actionability.json':
      (phase==='original'?'mobile-neutral-original-actionability.json':
      'mobile-neutral-restore-actionability.json');
    fs.writeFileSync(path.join(out,filename),JSON.stringify(evidence,null,2));
    record.push({
      name:phase==='s25-enter'?'MOBILE_S25_ENTER_ACTIONABILITY_TRACE_DIAGNOSTIC_ONLY':
        (phase==='original'?'MOBILE_NEUTRAL_ORIGINAL_ACTIONABILITY_TRACE_DIAGNOSTIC_ONLY':
        'MOBILE_NEUTRAL_RESTORE_ACTIONABILITY_TRACE_DIAGNOSTIC_ONLY'),
      filename,
      measured_playwright_tap_ms:tapElapsed,
      trusted_events:measurement.events?.filter(e=>e.trusted).map(e=>e.type)||[],
      samples:measurement.samples?.length||0,
      visual_acceptance:'NOT_GRANTED'
    });
    if(neutralScheduling) {
      try {
        const data=await finishFocusedSchedulingTrace(neutralScheduling);
        record.push({name:phase==='s25-enter'?'AA4_REAL_S25_SAME_INTERVAL_COMPOSITOR_AND_NODE_TRACE':'AA4_REAL_NEUTRAL_SAME_INTERVAL_COMPOSITOR_AND_NODE_TRACE',
          trace:data,tap_start_epoch_ms:tapStarted,
          tap_finish_epoch_ms:tapFinished,
          evidence:'Playwright original genuine tap + Chromium cc/viz trace + 100ms Node heartbeat',
          visual_acceptance:'NOT_GRANTED'});
      }catch(error){
        record.push({name:phase==='s25-enter'?'AA4_REAL_S25_SCHEDULING_READBACK_UNAVAILABLE':'AA4_REAL_NEUTRAL_SCHEDULING_READBACK_UNAVAILABLE',
          diagnostic_error:String(error),visual_acceptance:'NOT_GRANTED'});
      }
    }
    if(neutralSchedulingSession)await neutralSchedulingSession.detach().catch(()=>{});
  }
  return tapElapsed;
}

// PROOF ONLY: one short Chromium scheduler trace and low-rate Node-runner
// heartbeat around the first paused Canvas CDP tap, plus AA4 real Redo.
// Trace timestamps are monotonic Chromium microseconds, NOT wall-clock time;
// blink.user_timing marks and browser event epoch stamps permit correlation.
async function beginFocusedSchedulingTrace(cdp,label,
  categories='toplevel,latency,cc,blink.user_timing,gpu,toplevel.flow') {
  // V158: only the existing first paused 13-GLB canvas touch gets extra
  // GPU/Viz categories, conditionally discovered from this exact Chromium.
  // No extra input, WebGL frame, product flag, or <5000ms gate alteration.
  const deepCategories={requested:[],supported:[],missing:[],error:null};
  if(label.endsWith('-paused-canvas')){
    deepCategories.requested=[
      'gpu.angle','sync','viz',
      'disabled-by-default-gpu.debug',
      'disabled-by-default-gpu.service',
      'disabled-by-default-gpu.decoder',
      'disabled-by-default-gpu.device',
      'disabled-by-default-gpu_cmd_queue',
      'disabled-by-default-skia.gpu',
      'disabled-by-default-viz.gpu_composite_time'
    ];
    try{
      const available=new Set((await cdp.send('Tracing.getCategories')).categories||[]);
      deepCategories.supported=deepCategories.requested.filter(c=>available.has(c));
      deepCategories.missing=deepCategories.requested.filter(c=>!available.has(c));
      if(deepCategories.supported.length)
        categories+=','+deepCategories.supported.join(',');
    }catch(error){deepCategories.error=String(error);}
  }
  const started=Date.now(),cpu=process.cpuUsage(),resources=process.resourceUsage();
  const lag=monitorEventLoopDelay({resolution:100});lag.enable();
  const samples=[];let prev=nodePerformance.now();
  const timer=setInterval(()=>{
    const now=nodePerformance.now();
    if(samples.length<300)samples.push({
      epoch_ms:Date.now(),node_timer_lag_ms:Math.max(0,now-prev-100),
      host_loadavg_1m:os.loadavg()[0]});
    prev=now;
  },100);
  const state={cdp,label,started,cpu,resources,lag,samples,timer,
    categories,deepCategories,trace_started:false,trace_error:null};
  try{
    await cdp.send('Tracing.start',{
      // Chromium gpu::Scheduler uses the `gpu,toplevel.flow` trace categories
      // for ScheduleTask/RunNextTask and sync dependency flow. The previous
      // categories captured only the opaque toplevel RunTask wrapper.
      // This proof-only trace remains bounded to the existing two real gestures.
      categories,
      transferMode:'ReturnAsStream'});
    state.trace_started=true;
  }catch(error){state.trace_error=String(error);}
  return state;
}
async function finishFocusedSchedulingTrace(state) {
  if(!state)return null;
  clearInterval(state.timer);state.lag.disable();
  const cpu=process.cpuUsage(state.cpu);
  const resources=process.resourceUsage();
  const report={
    name:'FOCUSED_CHROMIUM_BROWSER_AND_RUNNER_SCHEDULING_DIAGNOSTIC_ONLY',
    label:state.label,source_sha:process.env.GITHUB_SHA||null,
    mode:NATIVE_AA4_MODE?'native_aa4_proof_only':'native_aa0',
    host_epoch_start_ms:state.started,host_epoch_finish_ms:Date.now(),
    node_runner_cpu_delta_us:cpu,
    node_runner_context_switch_delta:{
      voluntary:resources.voluntaryContextSwitches-state.resources.voluntaryContextSwitches,
      involuntary:resources.involuntaryContextSwitches-state.resources.involuntaryContextSwitches},
    node_event_loop_delay_max_ms:state.lag.max/1e6,
    node_timer_heartbeat_100ms:state.samples,
    chrome_trace_started:state.trace_started,
    chrome_trace_categories:state.categories,
    chrome_deep_gpu_category_preflight:state.deepCategories,
    chrome_trace_error:state.trace_error,
    strict_touch_threshold_ms_exclusive:5000,
    browser_process_cpu_and_os_thread_scheduler:'NOT_DIRECTLY_MEASURED',
    physical_samsung_test:false,product_or_visual_pass:'NOT_GRANTED'
  };
  if(state.trace_started) {
    try {
      const complete=new Promise(resolve=>{
        let finished=false;
        const onComplete=e=>{
          if(finished)return;finished=true;clearTimeout(timer);
          state.cdp.off('Tracing.tracingComplete',onComplete);resolve(e);
        };
        state.cdp.on('Tracing.tracingComplete',onComplete);
        const timer=setTimeout(()=>onComplete({timeout:true}),15000);
      });
      await state.cdp.send('Tracing.end');
      const result=await complete;
      if(!result.stream)throw Error('Chromium trace complete without stream: '+JSON.stringify(result));
      let chunks=[],bytes=0,truncated=false,done=false;
      while(!done){
        const item=await state.cdp.send('IO.read',{handle:result.stream,size:524288});
        const part=item.base64Encoded?Buffer.from(item.data||'','base64'):
          Buffer.from(item.data||'','utf8');
        bytes+=part.length;
        if(bytes<=24*1024*1024)chunks.push(part);else truncated=true;
        done=!!item.eof;
      }
      await state.cdp.send('IO.close',{handle:result.stream});
      report.chrome_trace_bytes_total=bytes;
      report.chrome_trace_truncated=truncated;
      if(!truncated){
        const raw=Buffer.concat(chunks);
        const target='mobile-focused-chromium-'+state.label+'.json.gz';
        fs.writeFileSync(path.join(out,target),zlib.gzipSync(raw));
        report.chrome_trace_file=target;
        const trace=JSON.parse(raw.toString('utf8'));
        const events=trace.traceEvents||[];
        report.chrome_trace_event_count=events.length;
        // GPU scheduler flow evidence is a Chromium user-space trace only:
        // it is NOT a Linux sched_switch record or an off-CPU stack sample.
        const gpuEvents=events.filter(e=>(e.cat||'').split(',').includes('gpu'));
        report.chrome_trace_gpu_event_count=gpuEvents.length;
        report.chrome_trace_gpu_scheduler_flow_events=gpuEvents
          .filter(e=>/Scheduler::(ScheduleTask|RunNextTask|SequenceUnblocked|ContinueTask)/.test(e.name||''))
          .slice(0,100).map(e=>({
            name:e.name,cat:e.cat,phase:e.ph,ts_us:e.ts,pid:e.pid,tid:e.tid,
            duration_us:e.dur??null,thread_cpu_duration_us:e.tdur??null,
            id:e.id??null,bind_id:e.bind_id??null,
            flow_ids:e.flow_ids??null
          }));
        report.chrome_trace_gpu_long_events=gpuEvents
          .filter(e=>Number(e.dur)>=10000)
          .sort((a,b)=>b.dur-a.dur).slice(0,20)
          .map(e=>({name:e.name,cat:e.cat,ts_us:e.ts,
            duration_us:e.dur,thread_cpu_duration_us:e.tdur??null,
            pid:e.pid,tid:e.tid}));
        report.chrome_trace_named_gpu_sync_candidates=events.filter(e=>
          /Fence|WaitSync|SyncToken|CommandBuffer|SwapBuffers|WaitableEvent|WaitFor|Semaphore|Flush|Finish|Swapchain/i.test(e.name||'') &&
          /gpu|viz|skia|sync|toplevel/i.test(e.cat||''))
          .slice(0,180).map(e=>({name:e.name,cat:e.cat,ph:e.ph,
            ts_us:e.ts,duration_us:e.dur??null,thread_cpu_duration_us:e.tdur??null,
            pid:e.pid,tid:e.tid,id:e.id??null,bind_id:e.bind_id??null}));
        report.chrome_trace_gpu_scope_caveat=
          'Named user-space GPU/Viz spans MAY reveal synchronization. Absence of a recorded primitive does NOT prove no wait; kernel, driver and physical S25 remain unmeasured.';
        report.chrome_trace_markers=events
          .filter(e=>/CELINE_E2E_(PAUSED_CANVAS|AA4_REDO|AA4_REAL_NEUTRAL|AA4_REAL_S25)/.test(e.name||''))
          .slice(0,10).map(e=>({name:e.name,ts_us:e.ts,pid:e.pid,tid:e.tid}));
        report.chrome_trace_long_scheduler_events=events
          .filter(e=>Number(e.dur)>=100000&&
            /RunTask|ThreadController|Scheduler|Compositor|BeginFrame|DrawFrame|Input|Touch|Dispatch/.test(e.name||''))
          .sort((a,b)=>b.dur-a.dur).slice(0,24)
          .map(e=>({name:e.name,category:e.cat,ts_us:e.ts,
            duration_ms:e.dur/1000,pid:e.pid,tid:e.tid}));
      }
    }catch(error){report.chrome_trace_error=String(error.stack||error);}
  }
  const filename='mobile-focused-runner-'+state.label+'.json';
  fs.writeFileSync(path.join(out,filename),JSON.stringify(report,null,2));
  return {file:filename,trace_file:report.chrome_trace_file||null,
    trace_error:report.chrome_trace_error||null,
    runner_delay_max_ms:report.node_event_loop_delay_max_ms,
    long_scheduler_events:report.chrome_trace_long_scheduler_events?.length||0};
}


// V145 proof-only: independently ping the renderer through a second CDP
// session WHILE the existing original WebGL canvas touch is outstanding.
// The probe issues no new touch or UI change; it cannot grant a PASS.
async function beginPassiveRendererCdpProbe(context,page,label) {
  const report={
    name:'V145_PASSIVE_RENDERER_CDP_RESPONSIVENESS_DURING_REAL_CANVAS_TOUCH',
    label,source_sha:process.env.GITHUB_SHA||null,
    epoch_started_ms:Date.now(),samples:[],ticks:[],
    caveat:'Read-only Runtime.evaluate on separate CDP session may itself perturb browser scheduling; this is not OS, GPU driver or physical S25 proof.',
    strict_touch_threshold_ms_exclusive:5000,visual_acceptance:'NOT_GRANTED'
  };
  let session=null,ongoing=null,inFlight=false,stopped=false,sequence=0;
  try{
    session=await context.newCDPSession(page);
    await session.send('Runtime.enable');
  }catch(error){
    report.install_error=String(error);
    if(session)await session.detach().catch(()=>{});
    return {stop:async()=>{
      fs.writeFileSync(path.join(out,'mobile-passive-renderer-cdp-'+label+'.json'),
        JSON.stringify(report,null,2));
      return {file:'mobile-passive-renderer-cdp-'+label+'.json',
        install_error:report.install_error};
    }};
  }
  const send=()=>{
    if(stopped)return;
    const epoch=Date.now();
    if(report.ticks.length<100)report.ticks.push({epoch_ms:epoch,pending:inFlight});
    if(inFlight||report.samples.length>=45)return;
    const id=++sequence;
    inFlight=true;
    ongoing=session.send('Runtime.evaluate',{
      expression:'performance.timeOrigin + performance.now()',
      returnByValue:true,silent:true
    }).then(response=>{
      const ack=Date.now();
      report.samples.push({sequence:id,sent_epoch_ms:epoch,
        ack_epoch_ms:ack,ack_elapsed_ms:ack-epoch,
        renderer_epoch_ms:response.result?.value??null,
        exception:response.exceptionDetails?.text??null});
    }).catch(error=>{
      report.samples.push({sequence:id,sent_epoch_ms:epoch,
        ack_epoch_ms:Date.now(),error:String(error)});
    }).finally(()=>{inFlight=false;});
  };
  const timer=setInterval(send,200);
  send();
  return {stop:async()=>{
    stopped=true;
    clearInterval(timer);
    const stopping=Date.now();
    if(ongoing)await Promise.race([ongoing,new Promise(resolve=>setTimeout(resolve,1500))]);
    report.epoch_stopped_ms=Date.now();
    report.stop_wait_ms=Date.now()-stopping;
    report.unresolved_at_stop=inFlight;
    report.max_acked_ms=report.samples.length?
      Math.max(...report.samples.filter(s=>s.ack_elapsed_ms!=null).map(s=>s.ack_elapsed_ms),0):null;
    await session.detach().catch(error=>{report.detach_error=String(error);});
    const file='mobile-passive-renderer-cdp-'+label+'.json';
    fs.writeFileSync(path.join(out,file),JSON.stringify(report,null,2));
    return {file,samples:report.samples.length,ticks:report.ticks.length,
      max_acked_ms:report.max_acked_ms,unresolved_at_stop:report.unresolved_at_stop};
  }};
}

async function independentNativeCdpInputReceipt(page,context) {
  // This is a measured, separate control AFTER mandatory genuine touch/JSON/
  // viewport gates. It cannot grant PASS, replace Playwright UI touches or
  // modify any of the 13 owner-frozen transforms, room, locks or camera.
  const sourceBefore=await page.evaluate(()=>
    window.__celineRoomEditorE2E.serializedLayout());
  const point=await page.evaluate(()=>{
    const canvas=document.querySelector('#viewport canvas');
    const b=canvas.getBoundingClientRect();
    const x=Math.round(b.x+b.width*.53),y=Math.round(b.y+b.height*.30);
    if(document.elementFromPoint(x,y)!==canvas)
      throw new Error('Independent real-CDP native control requires unobstructed WebGL canvas');
    window.__celineIndependentNativeInputEvents=[];
    for(const type of ['touchstart','pointerdown','touchend','pointerup'])
      document.addEventListener(type,e=>{
        if(e.target===canvas)
          window.__celineIndependentNativeInputEvents.push({
            type,epoch_ms:performance.timeOrigin+performance.now(),
            trusted:e.isTrusted,pointer_type:e.pointerType||null
          });
      },{capture:true,once:true});
    return {x,y};
  });
  const cdp=await context.newCDPSession(page);
  const began=Date.now(); let downAck,upAck;
  try {
    await cdp.send('Input.dispatchTouchEvent',{
      type:'touchStart',touchPoints:[{x:point.x,y:point.y,id:41}]
    });
    downAck=Date.now();
    await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
    upAck=Date.now();
  } finally {await cdp.detach();}
  const end=await page.evaluate(()=>({
    layout:window.__celineRoomEditorE2E.serializedLayout(),
    events:window.__celineIndependentNativeInputEvents||[],
    rendering:window.__celineRoomEditorE2E.mobileRenderStats()
  }));
  return {
    control:'UNMODIFIED_REAL_WEBGL_CANVAS_STATIONARY_TOUCH',
    mode:NATIVE_AA4_MODE?'native_msaa4':'native_aa0',
    point,dispatch_begin_epoch_ms:began,
    send_start_ack_ms:downAck-began,send_end_ack_ms:upAck-downAck,
    total_cdp_ack_ms:upAck-began,
    browser_events:end.events,
    native_touch_received:end.events.some(e=>e.type==='touchstart'&&e.trusted),
    native_pointer_received:end.events.some(e=>e.type==='pointerdown'&&e.trusted),
    // serializedLayout() returns a NEW parsed object every call. Comparing
    // object identity always yields false, even if every owner TRS/lock is
    // identical. Compare the complete ordered JSON values, not references;
    // any genuine room/object/legacy-field mutation must still fail closed.
    owner_json_unchanged:JSON.stringify(end.layout)===JSON.stringify(sourceBefore),
    renderer_after:end.rendering,
    product_acceptance:'NOT_GRANTED_BY_DIAGNOSTIC'
  };
}


async function controlledRenderPauseNativeTouchProbe(page,context) {
  // Proof-only diagnosis BEFORE mandatory user orbit gesture, while the exact
  // approved owner camera is naturally stationary; never grants acceptance.
  const readState=()=>page.evaluate(()=>({
    layout:window.__celineRoomEditorE2E.serializedLayout(),
    camera:window.__celineRoomEditorE2E.cameraProjection(),
    room:window.__celineRoomEditorE2E.auditScene().room_m
  }));
  // Require natural stillness before the first user orbit. The former late
  // diagnostic could never settle after damping and AA4 often failed earlier;
  // do not reset, clamp, or artificially change the frozen owner camera.
  const cameraSettle=await page.evaluate(async()=>{
    const began=performance.now();
    let previous=null,identicalFrames=0,frames=0;
    while(performance.now()-began<35000){
      await new Promise(resolve=>requestAnimationFrame(resolve));
      const position=window.__celineRoomEditorE2E.cameraProjection().camera_position_m;
      frames++;
      identicalFrames=previous&&position.every((v,i)=>Object.is(v,previous[i]))
        ?identicalFrames+1:0;
      previous=position;
      if(identicalFrames>=4)return {settled:true,frames,elapsed_ms:performance.now()-began};
    }
    return {settled:false,frames,elapsed_ms:performance.now()-began};
  });
  assert(cameraSettle.settled,
    'Native CDP causal trial requires naturally settled orbit; never reset owner camera');
  const before=await readState(),cdp=await context.newCDPSession(page),trials=[];
  let restoreError=null;
  try {
    for(const name of ['active_before','render_paused','active_after']) {
      const paused=name==='render_paused';
      await page.evaluate(p=>window.__celineRoomEditorE2E.proofMobileRenderLoopPause(p),paused);
      await page.waitForTimeout(paused?300:450);
      const pre=await page.evaluate(label=>{
        const canvas=document.querySelector('#viewport canvas'),r=canvas.getBoundingClientRect();
        const point={x:Math.round(r.x+r.width*.53),y:Math.round(r.y+r.height*.30)};
        if(document.elementFromPoint(point.x,point.y)!==canvas)
          throw new Error('CDP controlled input missed original unobstructed WebGL canvas');
        if(!window.__renderPauseTouchListenersInstalled){
          for(const type of ['pointerdown','pointerup','touchstart','touchend'])
            document.addEventListener(type,event=>{
              const current=window.__renderPauseTouchTrial;
              if(!current||event.target!==canvas||!event.isTrusted)return;
              const epoch_ms=performance.timeOrigin+performance.now();
              if(epoch_ms<current.started_epoch_ms)return;
              current.events.push({type,epoch_ms,
                original_event_epoch_ms:performance.timeOrigin+event.timeStamp,
                pointer_type:event.pointerType||null,trust:event.isTrusted,
                target:'original_webgl_canvas'});
            },{capture:true,passive:true});
          window.__renderPauseTouchListenersInstalled=true;
        }
        window.__renderPauseTouchTrial={name:label,
          started_epoch_ms:performance.timeOrigin+performance.now(),events:[]};
        return {point,started_epoch_ms:window.__renderPauseTouchTrial.started_epoch_ms,
          render_before:window.__celineRoomEditorE2E.mobileRenderStats()};
      },name);
      const started=Date.now();let startAck=null,endAck=null,error=null;
      try {
        await cdp.send('Input.dispatchTouchEvent',{
          type:'touchStart',touchPoints:[{...pre.point,id:73}]});
        startAck=Date.now();
        await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
        endAck=Date.now();
      }catch(e){
        error=String(e.stack||e);
        try{await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});}
        catch(_ignored){}
      }
      const finished=Date.now();
      const post=await page.evaluate(()=>({
        events:(window.__renderPauseTouchTrial?.events||[]).slice(),
        render_after:window.__celineRoomEditorE2E.mobileRenderStats(),
        frames:window.__celineRoomEditorE2E.proofMobileRecentFrames(),
        trace:window.__celineMobileLatencyTrace||null
      }));
      const inWindow=item=>item.epoch_ms>=pre.started_epoch_ms-250&&
        item.epoch_ms<=finished+250;
      const frames=post.frames.filter(inWindow);
      const gaps=(post.trace?.raf_gaps||[]).filter(inWindow);
      const tasks=(post.trace?.longtasks||[]).filter(inWindow);
      const pointer=post.events.find(e=>e.type==='pointerdown');
      trials.push({name,paused,point:pre.point,
        cdp_start_ack_ms:startAck===null?null:startAck-started,
        cdp_end_ack_ms:endAck===null||startAck===null?null:endAck-startAck,
        cdp_total_ack_ms:endAck===null?null:endAck-started,
        native_dispatch_error:error,
        native_trusted_pointer_received:!!pointer,
        native_trusted_touch_received:post.events.some(e=>e.type==='touchstart'),
        pointer_receipt_after_start_ms:pointer?pointer.epoch_ms-started:null,
        target_filtered_browser_events:post.events,
        render_count_delta:post.render_after.renders-pre.render_before.renders,
        render_frames:frames,
        max_raf_gap_ms:gaps.length?Math.max(...gaps.map(g=>g.gap_ms)):0,
        max_longtask_ms:tasks.length?Math.max(...tasks.map(t=>t.duration_ms)):0,
        relevant_longtasks:tasks});
    }
  } finally {
    try{await page.evaluate(()=>
      window.__celineRoomEditorE2E.proofMobileRenderLoopPause(false));}
    catch(e){restoreError=String(e.stack||e);}
    await cdp.detach();
  }
  const after=await readState();
  // Preserve genuine per-phase measurements even on failure of any strict
  // invariant. A diagnostic FAIL is never an app PASS or visual approval.
  fs.writeFileSync(path.join(out,'mobile-render-pause-cdp-preassert.json'),
    JSON.stringify({source_sha:process.env.GITHUB_SHA||null,
      mode:NATIVE_AA4_MODE?'native_msaa4':'native_aa0',
      camera_settle:cameraSettle,
      owner_layout_unchanged:JSON.stringify(after.layout)===JSON.stringify(before.layout),
      camera_unchanged:JSON.stringify(after.camera)===JSON.stringify(before.camera),
      room_unchanged:JSON.stringify(after.room)===JSON.stringify(before.room),
      camera_before:before.camera,camera_after:after.camera,
      trials,restoreError,product_acceptance:'NOT_GRANTED_BY_DIAGNOSTIC'},null,2));
  assert.deepEqual(after.layout,before.layout,'CDP causal test changed owner JSON/legacy fields');
  assert.deepEqual(after.camera,before.camera,'CDP causal test changed owner camera');
  assert.deepEqual(after.room,before.room,'CDP causal test changed room dimensions');
  assert.equal(restoreError,null,'CDP causal test did not restore render loop');
  return {source_scene:'13 original GLBs',mode:NATIVE_AA4_MODE?'native_msaa4':'native_aa0',
    experiment:'same scene native CDP active/paused/active render-loop comparison',
    paused_webgl_render_count_zero:trials.find(t=>t.paused)?.render_count_delta===0,
    trials,owner_json_camera_room_unchanged:true,physical_s25_test:false,
    product_acceptance:'NOT_GRANTED_BY_DIAGNOSTIC'};
}


/**
 * Proof-only differential: original real canvas versus a passive, visible HTML
 * target on the SAME 13-source-GLB scene with the Three render loop PAUSED.
 * Four native trusted CDP taps in alternating ABBA order mitigate warmup/order.
 * This measures CDP acknowledgement, trusted event timestamp versus passive
 * document capture-listener receipt, rAF/LongTask and sampled WebGL frames
 * separately; rAF is a COMPOSITOR PROXY, not a direct Chromium compositor trace.
 * It cannot alter production editor.js, camera, user JSON, or test limits.
 */
async function pausedCanvasVsPassiveHtmlNativeProbe(page,context) {
  const readOwner=()=>page.evaluate(()=>({
    layout:window.__celineRoomEditorE2E.serializedLayout(),
    camera:window.__celineRoomEditorE2E.cameraProjection(),
    room:window.__celineRoomEditorE2E.auditScene().room_m
  }));
  const before=await readOwner(),cdp=await context.newCDPSession(page),trials=[];
  let cleanupError=null,prepared=null;
  const getMetrics=async()=>{
    try {
      const m=(await cdp.send('Performance.getMetrics')).metrics;
      return Object.fromEntries(m.filter(x=>
        ['TaskDuration','ScriptDuration','LayoutDuration','RecalcStyleDuration',
         'LayoutCount','RecalcStyleCount'].includes(x.name)
      ).map(x=>[x.name,x.value]));
    }catch(e){return {unavailable:String(e.message||e)};}
  };
  try {
    await cdp.send('Performance.enable');
    await page.evaluate(()=>window.__celineRoomEditorE2E.proofMobileRenderLoopPause(true));
    await page.waitForTimeout(300);
    prepared=await page.evaluate(()=>{
      const canvas=document.querySelector('#viewport canvas');
      if(!canvas)throw new Error('13-source-GLB original WebGL canvas absent');
      const r=canvas.getBoundingClientRect();
      const el=document.createElement('div');
      el.id='__celine_e2e_passive_html_target';
      el.setAttribute('aria-label','Temporary passive HTML touch diagnostic only');
      el.style.cssText=[
        'position:fixed','width:44px','height:44px',
        'left:'+Math.round(Math.max(2,Math.min(innerWidth-46,r.left+r.width*.12-22)))+'px',
        'top:'+Math.round(Math.max(2,Math.min(innerHeight-46,r.top+r.height*.30-22)))+'px',
        'display:block','visibility:visible','background:#eeeeee',
        'border:2px solid #444444','box-sizing:border-box','opacity:1',
        'touch-action:none','pointer-events:auto','z-index:2147483646'
      ].join(';');
      document.body.appendChild(el);
      const canvasPoint={
        x:Math.round(r.left+r.width*.53),
        y:Math.round(r.top+r.height*.30)
      };
      const h=el.getBoundingClientRect();
      const htmlPoint={x:Math.round(h.left+h.width/2),y:Math.round(h.top+h.height/2)};
      if(document.elementFromPoint(canvasPoint.x,canvasPoint.y)!==canvas ||
         document.elementFromPoint(htmlPoint.x,htmlPoint.y)!==el)
        throw new Error('Canvas/HTML differential hit-test requires TWO unobstructed native targets');
      const state={canvas,html:el,events:[],current:null};
      const listener=event=>{
        if(!state.current||!event.isTrusted)return;
        const expected=state.current.target==='canvas'?canvas:el;
        if(event.target!==expected)return;
        const receipt=performance.timeOrigin+performance.now();
        state.events.push({
          trial:state.current.order,target:state.current.target,type:event.type,
          trusted:event.isTrusted,pointer_type:event.pointerType||null,
          browser_event_timestamp_epoch_ms:performance.timeOrigin+event.timeStamp,
          document_capture_receipt_epoch_ms:receipt,
          event_timestamp_to_listener_ms:receipt-(performance.timeOrigin+event.timeStamp)
        });
      };
      for(const type of ['pointerdown','pointerup','touchstart','touchend'])
        document.addEventListener(type,listener,{capture:true,passive:true});
      state.cleanup=()=>{
        for(const type of ['pointerdown','pointerup','touchstart','touchend'])
          document.removeEventListener(type,listener,true);
        el.remove();
      };
      window.__celineNativeTargetCompare=state;
      return {canvasPoint,htmlPoint,htmlRect:[h.x,h.y,h.width,h.height],
        screen:[innerWidth,innerHeight],original_canvas_rect:[r.x,r.y,r.width,r.height],
        owner_scene:'13 original source GLBs already loaded and owner JSON verified'};
    });
    for(let order=0;order<4;order++){
      const target=['canvas','html','html','canvas'][order];
      const pre=await page.evaluate(({target,order})=>{
        const s=window.__celineNativeTargetCompare;
        const point=target==='canvas'?
          {x:Math.round(s.canvas.getBoundingClientRect().left+
             s.canvas.getBoundingClientRect().width*.53),
           y:Math.round(s.canvas.getBoundingClientRect().top+
             s.canvas.getBoundingClientRect().height*.30)}:
          {x:Math.round(s.html.getBoundingClientRect().left+22),
           y:Math.round(s.html.getBoundingClientRect().top+22)};
        const expected=target==='canvas'?s.canvas:s.html;
        const hit=document.elementFromPoint(point.x,point.y);
        if(hit!==expected)throw new Error('Native '+target+' control covered: '+hit?.id);
        s.current={target,order};
        return {point,hit_test_pass:true,
          dispatch_preflight_browser_epoch_ms:performance.timeOrigin+performance.now(),
          event_offset:s.events.length,
          render_before:window.__celineRoomEditorE2E.mobileRenderStats(),
          screen:[innerWidth,innerHeight],scroll_y:scrollY};
      },{target,order});
      const perfBefore=await getMetrics();
      const focused=order===0?await beginFocusedSchedulingTrace(cdp,
        (NATIVE_AA4_MODE?'aa4':'aa0')+'-paused-canvas'):null;
      const rendererProbe=order===0?await beginPassiveRendererCdpProbe(context,page,
        (NATIVE_AA4_MODE?'aa4':'aa0')+'-paused-canvas'):null;
      if(focused)await page.evaluate(()=>
        performance.mark('CELINE_E2E_PAUSED_CANVAS_BEFORE')).catch(()=>{});
      const started=Date.now();
      let startAck=null,endAck=null,dispatchError=null;
      try{
        await cdp.send('Input.dispatchTouchEvent',{
          type:'touchStart',touchPoints:[{x:pre.point.x,y:pre.point.y,id:96+order}]});
        startAck=Date.now();
        await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
        endAck=Date.now();
      }catch(e){
        dispatchError=String(e.stack||e);
        try{await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});}
        catch(_ignored){}
      }
      const finished=Date.now();
      if(focused)await page.evaluate(()=>
        performance.mark('CELINE_E2E_PAUSED_CANVAS_AFTER')).catch(()=>{});
      const passiveRendererSummary=rendererProbe?await rendererProbe.stop():null;
      const focusedSummary=await finishFocusedSchedulingTrace(focused);
      const perfAfter=await getMetrics();
      const post=await page.evaluate(offset=>{
        const s=window.__celineNativeTargetCompare;
        return {events:s.events.slice(offset),
          render_after:window.__celineRoomEditorE2E.mobileRenderStats(),
          frames:window.__celineRoomEditorE2E.proofMobileRecentFrames(),
          trace:window.__celineMobileLatencyTrace||null,
          hit_target:document.elementFromPoint(
            s.current.target==='canvas'?
              Math.round(s.canvas.getBoundingClientRect().left+s.canvas.getBoundingClientRect().width*.53):
              Math.round(s.html.getBoundingClientRect().left+22),
            s.current.target==='canvas'?
              Math.round(s.canvas.getBoundingClientRect().top+s.canvas.getBoundingClientRect().height*.30):
              Math.round(s.html.getBoundingClientRect().top+22)
          )=== (s.current.target==='canvas'?s.canvas:s.html),
          scroll_y:scrollY};
      },pre.event_offset);
      const between=e=>e.epoch_ms>=pre.dispatch_preflight_browser_epoch_ms-150&&
        e.epoch_ms<=finished+150;
      const raf=(post.trace?.raf_gaps||[]).filter(between);
      const tasks=(post.trace?.longtasks||[]).filter(between);
      const frames=post.frames.filter(between);
      const pointer=post.events.find(e=>e.type==='pointerdown'&&e.trusted);
      const touch=post.events.find(e=>e.type==='touchstart'&&e.trusted);
      const cdpTotal=endAck===null?null:endAck-started;
      trials.push({order,target,point:pre.point,
        hit_test_before:pre.hit_test_pass,hit_test_after:post.hit_target,
        browser_viewport_css:pre.screen,scroll_before:pre.scroll_y,scroll_after:post.scroll_y,
        playwright_control_start_epoch_ms:started,
        playwright_control_end_epoch_ms:finished,
        playwright_total_elapsed_ms:finished-started,
        cdp_touch_start_ack_elapsed_ms:startAck===null?null:startAck-started,
        cdp_touch_end_ack_elapsed_ms:endAck===null||startAck===null?null:endAck-startAck,
        cdp_total_ack_elapsed_ms:cdpTotal,
        strict_under_5000ms:cdpTotal!==null&&cdpTotal<5000,
        dispatch_error:dispatchError,
        trusted_touchstart_received:!!touch,
        trusted_pointerdown_received:!!pointer,
        pointer_listener_after_dispatch_ms:pointer?
          pointer.document_capture_receipt_epoch_ms-started:null,
        pointer_event_timestamp_to_listener_ms:pointer?.event_timestamp_to_listener_ms??null,
        document_capture_browser_events:post.events,
        paused_webgl_render_count_delta:post.render_after.renders-pre.render_before.renders,
        paused_webgl_render_frames:frames,
        browser_raf_gap_max_ms:raf.length?Math.max(...raf.map(x=>x.gap_ms)):0,
        browser_longtask_max_ms:tasks.length?Math.max(...tasks.map(x=>x.duration_ms)):0,
        browser_longtask_count:tasks.length,
        browser_renderer_metrics_before:perfBefore,browser_renderer_metrics_after:perfAfter,
        compositor_direct_measurement:'NOT_AVAILABLE__RAF_GAP_IS_ONLY_PROXY',
        passive_renderer_cdp_responsiveness:passiveRendererSummary,
        targeted_chromium_runner_trace:focusedSummary});
    }
  } finally {
    try{
      await page.evaluate(()=>{
        const s=window.__celineNativeTargetCompare;
        if(s){s.cleanup();delete window.__celineNativeTargetCompare;}
        window.__celineRoomEditorE2E.proofMobileRenderLoopPause(false);
      });
    }catch(e){
      cleanupError=String(e.stack||e);
      try{await page.evaluate(()=>
        window.__celineRoomEditorE2E.proofMobileRenderLoopPause(false));}
      catch(_ignored){}
    }
    await cdp.detach();
  }
  const after=await readOwner();
  const report={
    source_sha:process.env.GITHUB_SHA||null,
    mode:NATIVE_AA4_MODE?'native_msaa4_proof_only':'native_aa0_production_default',
    scene:'exact original owner 13 GLBs in 412x915 Chromium mobile browser',
    comparison:'alternating real native CDP touch on canvas/visible passive HTML while WebGL render paused',
    strict_threshold_ms_exclusive:5000,
    prepared,trials,cleanupError,
    strict_5s_all_taps:trials.length===4&&trials.every(t=>t.strict_under_5000ms),
    trusted_target_events_all_received:trials.length===4&&
      trials.every(t=>t.trusted_touchstart_received&&t.trusted_pointerdown_received),
    paused_render_zero_all:trials.length===4&&
      trials.every(t=>t.paused_webgl_render_count_delta===0),
    all_hit_tests_unobstructed:trials.length===4&&
      trials.every(t=>t.hit_test_before&&t.hit_test_after),
    owner_json_unchanged:JSON.stringify(before.layout)===JSON.stringify(after.layout),
    owner_camera_unchanged:JSON.stringify(before.camera)===JSON.stringify(after.camera),
    room_dimensions_unchanged:JSON.stringify(before.room)===JSON.stringify(after.room),
    physical_samsung_test:false,visual_acceptance:'NOT_GRANTED',
    causality_limit:'CDP ACK, browser listener, rAF and renderer metrics separated; direct compositor/SwiftShader time is not independently instrumented'
  };
  fs.writeFileSync(path.join(out,'mobile-paused-webgl-versus-html-native-cdp.json'),
    JSON.stringify(report,null,2));
  assert.deepEqual(after.layout,before.layout,'Native canvas/HTML differential changed owner JSON');
  assert.deepEqual(after.camera,before.camera,'Native canvas/HTML differential changed camera');
  assert.deepEqual(after.room,before.room,'Native canvas/HTML differential changed room size');
  assert.equal(cleanupError,null,'Failed to remove native HTML probe and restore rendering');
  assert.equal(report.paused_render_zero_all,true,'Paused probe generated WebGL frames');
  assert.equal(report.all_hit_tests_unobstructed,true,'Differential touched obscured element');
  assert.equal(report.trusted_target_events_all_received,true,'CDP native trusted touch never reached target');
  return report; // slow touches remain FAIL evidence; cannot imply AA4/visual PASS
}

const crypto=require('node:crypto');
// V43: independent native original-GLB WebGL capture, so failure of the
// Chromium compositor screenshot no longer destroys all visual diagnostics.
async function captureRealWebGL(page,filename){
  const data=await page.evaluate(()=>({
    data_url:window.__celineRoomEditorE2E.framePng({clean:true}),
    canvas_css:((r)=>[r.width,r.height])(document.querySelector('#viewport canvas').getBoundingClientRect()),
    screen_css:[innerWidth,innerHeight],
    device_pixel_ratio:devicePixelRatio
  }));
  assert.match(data.data_url,/^data:image\/png;base64,/);
  const bytes=Buffer.from(data.data_url.slice('data:image/png;base64,'.length),'base64');
  assert(bytes.length>2000,'Real WebGL canvas PNG is unexpectedly small');
  assert.equal(bytes.subarray(1,4).toString('ascii'),'PNG');
  fs.writeFileSync(path.join(out,filename),bytes);
  return {filename,sha256:crypto.createHash('sha256').update(bytes).digest('hex'),
    bytes:bytes.length,pixels:[bytes.readUInt32BE(16),bytes.readUInt32BE(20)],
    screen_css:data.screen_css,canvas_css:data.canvas_css,dpr:data.device_pixel_ratio};
}
// A file input change handler awaits File.text() before applyState().  Do not
// schedule another import or claim JSON success until the preceding handler
// has actually reported completion.  This is a 5s strict readiness gate, not
// a longer timeout or a bypass of the real 13-GLB transform assertions.
async function completedMobileJsonImport(page,filename,stage) {
  const started=Date.now(), basename=path.basename(filename);
  await page.locator('#file').setInputFiles(filename);
  try {
    await page.waitForFunction((name)=>{
      const message=document.getElementById('status')?.textContent||'';
      return message==='Gespeichertes Layout geladen: '+name ||
        message.startsWith('Layout nicht importiert:');
    },basename,{timeout:5000,polling:100});
  } catch (err) {
    const diagnostic=await page.evaluate(()=>({
      status:document.getElementById('status')?.textContent||'',
      selectedFile:document.getElementById('file')?.files?.[0]?.name||null,
      bedX:window.__celineRoomEditorE2E.auditScene().objects
        .find(o=>o.id==='room_bed')?.transform.translation_m[0],
      renderer:window.__celineRoomEditorE2E.mobileRenderStats()
    })).catch(e=>({readback_error:String(e)}));
    record.push({name:stage+'_TIMEOUT',elapsed_ms:Date.now()-started,diagnostic});
    throw new Error(stage+': async import confirmation exceeded unchanged 5000ms: '+
      JSON.stringify(diagnostic)+'; '+String(err));
  }
  const message=await page.locator('#status').textContent();
  record.push({name:stage+'_HANDLER_FINISHED',elapsed_ms:Date.now()-started,status:message});
  assert.equal(message,'Gespeichertes Layout geladen: '+basename,
    stage+': actual importer rejected the JSON');
}
// Isolate the compositor in fresh, otherwise empty Chromium processes BEFORE
// loading any GLBs. Neither control is a screenshot of the user room.
const chromiumArgs=[
  '--enable-webgl','--use-gl=angle','--use-angle=swiftshader',
  '--enable-unsafe-swiftshader','--disable-dev-shm-usage',
  ...(GPU_VSYNC_PROBE==='no-vsync'?['--disable-gpu-vsync']:[])
];
async function probeEmptyMobileViewport(label,channel){
  let browser;
  const started=Date.now();
  try {
    browser=await chromium.launch({headless:true,
      ...(channel?{channel}:{}),args:chromiumArgs});
    const context=await browser.newContext({
      viewport:{width:412,height:915},screen:{width:412,height:915},
      deviceScaleFactor:3,isMobile:true,hasTouch:true
    });
    const page=await context.newPage();
    await page.setContent('<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head><body>Isolated compositor diagnostic only</body></html>');
    const bytes=await page.screenshot({timeout:3000,scale:'css'});
    const dimensions=[bytes.readUInt32BE(16),bytes.readUInt32BE(20)];
    assert.deepEqual(dimensions,[412,915],'Diagnostic blank viewport must be exactly 412x915');
    // V153 negative control: independent HTML/2D canvas with one genuine Chrome
    // touchscreen tap and the SAME gpu/cc/flow tracing as the real 13-GLB tap.
    // It is never counted as a room proof, never changes the strict 5s gate,
    // and has no connection to the immutable S25 JSON, GLBs or app runtime.
    const controlLabel=label.replace(/[^a-z0-9-]/gi,'-');
    const controlFile='mobile-real-2d-touch-control-'+controlLabel+'.json';
    let twoDControl={verdict:'DIAGNOSTIC_ONLY_NOT_PRODUCT_PASS',label,
      input_kind:'real Playwright touch on independent HTML/2D canvas',
      original_scene:'NOT_LOADED_IN_THIS_CONTROL',visual_acceptance:'NOT_GRANTED',
      strict_original_5000ms_gate:'UNCHANGED'};
    let controlCdp=null,controlTrace=null;
    try{
      await page.evaluate(()=>{
        const canvas=document.createElement('canvas');
        canvas.width=100;canvas.height=100;
        canvas.style.cssText='position:fixed;top:70px;left:15px;width:100px;height:100px';
        const ctx=canvas.getContext('2d');
        if(!ctx)throw new Error('2D canvas context unavailable');
        document.body.appendChild(canvas);
        const button=document.createElement('button');
        button.id='celine-2d-control-button';button.textContent='2D touch control';
        button.style.cssText='position:fixed;left:235px;top:180px;width:154px;height:58px;z-index:99';
        document.body.appendChild(button);
        const d={events:[],raf_count:0,max_raf_gap_ms:0,frames_start_epoch_ms:Date.now()};
        for(const name of ['pointerdown','touchstart','click']){
          button.addEventListener(name,e=>{
            d.events.push({type:name,epoch_ms:performance.timeOrigin+performance.now(),
              trusted:e.isTrusted,pointer_type:e.pointerType||null});
          },{capture:true});
        }
        let lastFrame=null,stopped=false,frameId=0;
        const frame=now=>{
          if(stopped)return;
          if(lastFrame!==null)d.max_raf_gap_ms=Math.max(d.max_raf_gap_ms,now-lastFrame);
          lastFrame=now;d.raf_count++;
          ctx.clearRect(0,0,100,100);ctx.fillStyle='rgb(30,60,100)';
          ctx.fillRect(d.raf_count%85,5,15,15);
          frameId=requestAnimationFrame(frame);
        };
        frameId=requestAnimationFrame(frame);
        window.__celine2dControlStop=()=>{
          stopped=true;cancelAnimationFrame(frameId);
          d.frames_end_epoch_ms=Date.now();
          d.target_rect=(()=>{const r=button.getBoundingClientRect();
            return {x:r.x,y:r.y,width:r.width,height:r.height};})();
          d.hit_target_at_center=(()=>{const r=button.getBoundingClientRect();
            return document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)?.id||null;})();
          d.scroll_y=scrollY;return d;
        };
      });
      controlCdp=await context.newCDPSession(page);
      controlTrace=await beginFocusedSchedulingTrace(controlCdp,
        'pure-2d-'+controlLabel,'toplevel,latency,cc,blink.user_timing,gpu,toplevel.flow');
      const tapStarted=Date.now();let tapError=null;
      try{await page.locator('#celine-2d-control-button').tap({timeout:12000});}
      catch(error){tapError=String(error.stack||error);}
      const tapFinished=Date.now();
      const browserData=await page.evaluate(()=>window.__celine2dControlStop())
        .catch(error=>({read_error:String(error)}));
      twoDControl={...twoDControl,tap_start_epoch_ms:tapStarted,
        tap_finish_epoch_ms:tapFinished,tap_elapsed_ms:tapFinished-tapStarted,
        tap_error:tapError,real_browser_2d:browserData,
        first_trusted_pointerdown_after_start_ms:browserData.events?.find(e=>
          e.type==='pointerdown'&&e.trusted)?.epoch_ms-tapStarted??null,
        caveat:'Separate no-WebGL Chrome page is a negative control, not proof of a kernel fence, driver wait or physical S25 behavior.'};
    }catch(error){twoDControl.control_error=String(error.stack||error);}
    finally{
      if(controlTrace)try{twoDControl.trace=await finishFocusedSchedulingTrace(controlTrace);}
        catch(error){twoDControl.trace_error=String(error);}
      if(controlCdp)await controlCdp.detach().catch(error=>{
        twoDControl.detach_error=String(error);
      });
      fs.writeFileSync(path.join(out,controlFile),JSON.stringify(twoDControl,null,2));
    }
    return {label,verdict:'PASS_BLANK_DIAGNOSTIC_ONLY',
      browser_version:browser.version(),elapsed_ms:Date.now()-started,
      sha256:crypto.createHash('sha256').update(bytes).digest('hex'),pixels:dimensions,
      two_d_control_file:controlFile,
      two_d_touch_elapsed_ms:twoDControl.tap_elapsed_ms??null,
      two_d_pointerdown_ms:twoDControl.first_trusted_pointerdown_after_start_ms??null};
  } catch(err) {
    return {label,verdict:'FAIL_BLANK_DIAGNOSTIC_ONLY',
      elapsed_ms:Date.now()-started,error:String(err.stack||err)};
  } finally {if(browser)await browser.close();}
}
async function main(){
  // One extra diagnostic variant on pinned original Chromium/13 GLBs; the
  // ordinary genuine Playwright AA0+AA4 gate and captures stay authoritative.
  const gpuProbe={source_sha:process.env.GITHUB_SHA||null,
    variant:GPU_VSYNC_PROBE,chromium_args:chromiumArgs,
    original_owner_layout_unchanged:true,visual_acceptance:'NOT_GRANTED'};
  fs.writeFileSync(path.join(out,'mobile-gpu-vsync-variant.json'),
    JSON.stringify(gpuProbe,null,2));
  record.push({name:'CHROMIUM_GPU_VSYNC_DIAGNOSTIC_ONLY',...gpuProbe});
  // Pairwise single-flight control: default headless shell versus new/full
  // Chromium headless. Historic tests exercised only the former; the genuine
  // room viewport below is still a mandatory independent acceptance gate.
  const isolatedControls=[
    await probeEmptyMobileViewport('playwright-default-headless-shell',null),
    await probeEmptyMobileViewport('full-chromium-new-headless','chromium')
  ];
  fs.writeFileSync(path.join(out,'mobile-isolated-compositor-controls.json'),
    JSON.stringify({source_sha:process.env.GITHUB_SHA||null,isolatedControls},null,2));
  record.push({name:'ISOLATED_CHROMIUM_CAPTURE_MODE_COMPARISON_DIAGNOSTIC_ONLY',
    isolatedControls});
  // Use the Playwright-installed FULL Chromium rather than its different
  // headless-shell renderer for the existing genuine 13-source-GLB workflow.
  // Do not change the strict 412x915 viewport, captures or furniture state.
  const browser=await chromium.launch({headless:true,channel:'chromium',
    args:chromiumArgs});
  let mobilePage=null;
  try {
    const context=await browser.newContext({
      viewport:{width:412,height:915},screen:{width:412,height:915},
      deviceScaleFactor:3,isMobile:true,hasTouch:true,acceptDownloads:true
    });
    const page=await context.newPage();
    mobilePage=page;
    activeProofPage=page;
    activeProofContext=context;

    // Instrument real browser event receipt / actionability long tails without
    // changing editor.js or replacing any real Playwright taps.
    // DEBUG=pw:api separately gives locator stable/scroll/dispatch timestamps.
    await page.addInitScript(()=>{
      const trace={events:[],longtasks:[],raf_gaps:[],
        browser_epoch_origin_ms:performance.timeOrigin};
      window.__celineMobileLatencyTrace=trace;
      const push=(a,item,limit)=>{a.push(item);if(a.length>limit)a.shift();};
      const targetName=(node)=>{
        if(!(node instanceof Element))return null;
        const key=node.closest('[data-mobile-action],[data-mobile-zoom],[data-nudge],button,summary');
        if(!key)return node.id||node.tagName;
        return key.id||key.getAttribute('data-mobile-action')||
          key.getAttribute('data-mobile-zoom')||
          key.getAttribute('data-nudge')||key.tagName;
      };
      const targetScope=(node)=>{
        if(!(node instanceof Element))return null;
        const key=node.closest('[data-mobile-action],[data-mobile-zoom],[data-nudge],button,summary');
        if(!key)return null;
        return key.closest('details')?.id||
          (key.closest('.mobile-extra')?'mobile-extra':null);
      };
      for(const type of ['touchstart','touchend','pointerdown','pointerup','click'])
        document.addEventListener(type,event=>{
          push(trace.events,{
            type,epoch_ms:performance.timeOrigin+performance.now(),
            target:targetName(event.target),
            scope:targetScope(event.target),
            event_original_epoch_ms:performance.timeOrigin+event.timeStamp,
            trusted:event.isTrusted,pointer_type:event.pointerType||null
          },1600);
        },{capture:true,passive:true});
      try {
        const observer=new PerformanceObserver(list=>{
          for(const entry of list.getEntries())
            push(trace.longtasks,{
              epoch_ms:performance.timeOrigin+entry.startTime,
              duration_ms:entry.duration,name:entry.name
            },500);
        });
        observer.observe({type:'longtask',buffered:true});
      } catch(error) {trace.longtasks_unavailable=String(error);}
      let lastFrame=0;
      const frame=now=>{
        if(lastFrame&&now-lastFrame>125)
          push(trace.raf_gaps,{
            epoch_ms:performance.timeOrigin+now,gap_ms:now-lastFrame
          },700);
        lastFrame=now;
        requestAnimationFrame(frame);
      };
      requestAnimationFrame(frame);
    });
    page.on('pageerror',e=>errors.push('PAGE '+e.message));
    page.on('console',m=>{if(m.type()==='error')errors.push('CONSOLE '+m.text());});
    page.on('response',r=>{
      if(r.status()>=400 && r.url().includes('127.0.0.1'))errors.push('HTTP '+r.status()+' '+r.url());
    });
    await page.route('https://cdn.jsdelivr.net/npm/three@0.164.1/**',async route=>{
      const relative=decodeURIComponent(new URL(route.request().url()).pathname.split('/three@0.164.1/')[1]||'');
      if(!/^(build\/three\.module\.js|examples\/jsm\/[a-zA-Z0-9_./-]+\.js)$/.test(relative)||relative.includes('..'))
        return route.abort();
      const file=path.resolve(repo,'node_modules/three',relative);
      if(!fs.existsSync(file))return route.abort();
      return route.fulfill({path:file,contentType:'text/javascript'});
    });
    // Read-only localhost-E2E texture inspection; original editor.js is NOT changed.
    const s25ColorFilterProbeInjection="\n    // E2E-ONLY: original offline S25 viewer uploads RGBA8 and reads gamma\n    // texture values directly. Three GLTFLoader colorSpace=SRGB uploads SRGB8_ALPHA8,\n    // decodes texels before filtering, then encodes shader output. This is a\n    // falsifiable pixel/derivative difference, NOT a proposed product correction.\n    s25CurtainOwnerRawTextureFilterRasterProbe(){\n      if(!s25RawPreview||[...entries.values()].filter(e=>e.loaded).length!==13)\n        throw new Error('Immutable S25 13-GLB source view required');\n      const w=renderer.domElement.width,h=renderer.domElement.height;\n      if(w!==618||h!==768)throw new Error('Native S25 618x768 view required');\n      const found=[];\n      const entry=entries.get('room_window_drapes');\n      if(!entry?.loaded)throw new Error('Original curtain GLB missing');\n      entry.group.traverse(mesh=>{\n        if(!mesh.isMesh)return;\n        const mats=Array.isArray(mesh.material)?mesh.material:[mesh.material];\n        for(let i=0;i<mats.length;i++)\n          if(mats[i]?.name==='S25_UNLIT_DISPLAY_material.010')\n            found.push({mesh,slot:i,base:mats[i]});\n      });\n      if(found.length!==1||!found[0].base.map)\n        throw new Error('Exactly one textured original curtain material expected');\n      const {mesh,slot,base}=found[0],sourceMap=base.map;\n      if(sourceMap.colorSpace!==THREE.SRGBColorSpace||!base.isMeshBasicMaterial||\n         base.toneMapped!==false||base.transparent!==false)\n        throw new Error('Unexpected current color-space/material contract');\n      const rawMap=sourceMap.clone();\n      rawMap.colorSpace=THREE.NoColorSpace;\n      rawMap.needsUpdate=true;\n      const rawMaterial=base.clone();\n      rawMaterial.name='E2E_OWNER_RGBA8_RAW_RGB_OUTPUT_ONLY';\n      rawMaterial.map=rawMap;\n      rawMaterial.onBeforeCompile=shader=>{\n        const token='#include <colorspace_fragment>';\n        if(!shader.fragmentShader.includes(token))\n          throw new Error('Raw S25 output conversion seam missing');\n        shader.fragmentShader=shader.fragmentShader.replace(token,\n          'gl_FragColor.rgb = clamp(gl_FragColor.rgb, 0.0, 1.0);');\n      };\n      rawMaterial.customProgramCacheKey=()=>'E2E_OWNER_RGBA8_RAW_RGB_OUTPUT_V1';\n      rawMaterial.needsUpdate=true;\n      const originalMaterial=mesh.material;\n      const json=snapshot(),cameraBefore=camera.position.toArray(),\n        targetBefore=orbit.target.toArray(),fovBefore=camera.fov,\n        renderTargetBefore=renderer.getRenderTarget();\n      const grid=floor.children.find(c=>c.userData.roomEditorGuideGrid===true);\n      const gridBefore=grid?.visible,gizmoBefore=transform.visible;\n      const gl=renderer.getContext(),captures=[];\n      const shot=(name)=>{\n        renderer.setRenderTarget(null);\n        renderer.render(scene,camera);\n        const pixels=[];\n        for(const [x,y] of [[435,384],[450,267]]){\n          const b=new Uint8Array(4);\n          gl.readPixels(x,h-y-1,1,1,gl.RGBA,gl.UNSIGNED_BYTE,b);\n          pixels.push({xy:[x,y],rgb:[b[0],b[1],b[2]]});\n        }\n        if(gl.getError()!==gl.NO_ERROR)throw new Error('GPU readPixels error');\n        captures.push({name,pixels,png_data_url:renderer.domElement.toDataURL('image/png')});\n      };\n      let identityRestored=false;\n      try{\n        if(grid)grid.visible=false;\n        transform.visible=false;\n        shot('three_srgb8_linear_filter_and_output_oetf');\n        if(Array.isArray(originalMaterial)){\n          const trial=originalMaterial.slice();trial[slot]=rawMaterial;\n          mesh.material=trial;\n        }else mesh.material=rawMaterial;\n        shot('owner_rgba8_gamma_filter_raw_output');\n      }finally{\n        mesh.material=originalMaterial;\n        if(grid)grid.visible=gridBefore;\n        transform.visible=gizmoBefore;\n        renderer.setRenderTarget(renderTargetBefore);\n        renderer.render(scene,camera);\n        identityRestored=mesh.material===originalMaterial&&base.map===sourceMap&&\n          sourceMap.colorSpace===THREE.SRGBColorSpace;\n        rawMaterial.dispose();\n        rawMap.dispose();\n      }\n      if(!identityRestored||snapshot()!==json||\n         camera.position.toArray().some((v,i)=>v!==cameraBefore[i])||\n         orbit.target.toArray().some((v,i)=>v!==targetBefore[i])||\n         camera.fov!==fovBefore)\n        throw new Error('Raw RGB experiment changed owner source/camera/JSON');\n      return {read_only:true,visual_acceptance:'NOT_GRANTED',\n        source_glbs:13,room_m:{...room},drawing_buffer_px:[w,h],\n        hypothesis:'RGBA8 gamma-encoded filtering vs SRGB8_ALPHA8 linear filtering',\n        original_offline_source:'gl.texImage2D(TEXTURE_2D,0,RGBA,RGBA,UNSIGNED_BYTE,bitmap)',\n        current_three_source:'three@0.164.1 SRGBColorSpace => SRGB8_ALPHA8',\n        tested_temporary_source:'THREE.NoColorSpace => RGBA8; no output OETF only on one cloned drape',\n        original_map_color_space:sourceMap.colorSpace,\n        source_texture_source_identity_preserved:true,\n        source_material_identity_restored:identityRestored,\n        original_camera_position_m:cameraBefore,\n        selected_pixels:[[435,384],[450,267]],captures};\n    },\n";
    const s25TextureAuditInjection="    s25CurtainTextureRuntimeAudit(){\n      if(!s25RawPreview)throw new Error('S25 raw preview not enabled');\n      const entry=entries.get('room_window_drapes');\n      if(!entry?.loaded)throw new Error('Original curtain not loaded');\n      const candidates=[];\n      entry.group.traverse(mesh=>{\n        if(!mesh.isMesh)return;\n        const mats=Array.isArray(mesh.material)?mesh.material:[mesh.material];\n        for(const material of mats)\n          if(material?.map&&material.name==='S25_UNLIT_DISPLAY_material.010')\n            candidates.push(material);\n      });\n      if(candidates.length!==1)throw new Error('Expected one source curtain texture; actual '+candidates.length);\n      const material=candidates[0],map=material.map;\n      const img=map.image||map.source?.data;\n      if(!img||img.width!==1024||img.height!==1024)\n        throw new Error('Original curtain JPEG must decode 1024x1024');\n      const decoded=document.createElement('canvas');\n      decoded.width=img.width;decoded.height=img.height;\n      const ctx=decoded.getContext('2d',{willReadFrequently:true});\n      if(!ctx)throw new Error('Original ImageBitmap 2D decoding unavailable');\n      ctx.drawImage(img,0,0);\n      const rgb=(x,y)=>Array.from(ctx.getImageData(x,y,1,1).data).slice(0,3);\n      const texture={\n        image_type:img.constructor?.name||null,\n        image_size_px:[img.width,img.height],\n        flipY:map.flipY,colorSpace:map.colorSpace,\n        magFilter:map.magFilter,minFilter:map.minFilter,\n        wrapS:map.wrapS,wrapT:map.wrapT,generateMipmaps:map.generateMipmaps,\n        premultiplyAlpha:map.premultiplyAlpha,unpackAlignment:map.unpackAlignment,\n        mapping:map.mapping,offset:map.offset.toArray(),repeat:map.repeat.toArray(),\n        center:map.center.toArray(),rotation:map.rotation,\n        uv_transform:map.matrix.elements.slice(),\n        outputColorSpace:renderer.outputColorSpace,toneMapping:renderer.toneMapping\n      };\n      const target=new THREE.WebGLRenderTarget(1,1,{depthBuffer:false,stencilBuffer:false});\n      target.texture.colorSpace=THREE.SRGBColorSpace;\n      const mat=new THREE.MeshBasicMaterial({\n        map,color:0xffffff,toneMapped:false,depthWrite:false,depthTest:false\n      });\n      const quad=new THREE.PlaneGeometry(2,2);\n      const plane=new THREE.Mesh(quad,mat);plane.frustumCulled=false;\n      const probeScene=new THREE.Scene();probeScene.add(plane);\n      const probeCamera=new THREE.OrthographicCamera(-1,1,1,-1,0.1,10);\n      probeCamera.position.z=2;\n      const oldTarget=renderer.getRenderTarget();\n      const oldViewport=renderer.getViewport(new THREE.Vector4());\n      const oldScissor=renderer.getScissor(new THREE.Vector4());\n      const oldScissorTest=renderer.getScissorTest();\n      const sampleCoordinates=[[103,213],[717,859],[421,501],[902,102]];\n      const samples=[];\n      try{\n        for(const [x,y] of sampleCoordinates){\n          const u=(x+0.5)/img.width,v=1-(y+0.5)/img.height;\n          const uv=quad.getAttribute('uv');\n          for(let i=0;i<uv.count;i++)uv.setXY(i,u,v);\n          uv.needsUpdate=true;\n          renderer.setRenderTarget(target);\n          renderer.setViewport(0,0,1,1);\n          renderer.setScissorTest(false);\n          renderer.render(probeScene,probeCamera);\n          const bytes=new Uint8Array(4);\n          renderer.readRenderTargetPixels(target,0,0,1,1,bytes);\n          const gpu=Array.from(bytes).slice(0,3);\n          const top=rgb(x,y),mirror=rgb(x,img.height-1-y);\n          const mae=(a,b)=>a.reduce((sum,c,i)=>sum+Math.abs(c-b[i]),0)/3;\n          samples.push({source_xy:[x,y],sample_uv:[u,v],gpu_rgb:gpu,\n            decoded_top_rgb:top,decoded_mirrored_rgb:mirror,\n            error_top_rgb_mae:mae(gpu,top),error_mirror_rgb_mae:mae(gpu,mirror),\n            gpu_alpha:bytes[3]});\n        }\n      }finally{\n        renderer.setRenderTarget(oldTarget);\n        renderer.setViewport(oldViewport);\n        renderer.setScissor(oldScissor);\n        renderer.setScissorTest(oldScissorTest);\n        quad.dispose();mat.dispose();target.dispose();\n      }\n      return {read_only:true,source_material:'material.010',\n        image_source:'existing immutable GLTFLoader decoded original JPEG',\n        pixel_probe:'real 1x1 offscreen Three.js renderer; fixed UV and decoded JPEG comparison',\n        texture,samples};\n    },\n";
    const s25TintProbeInjection="    selectedTintFramebufferAudit(){\n      if(!s25RawPreview||!s25RawViewBackup||selected?.id!=='room_bed')\n        throw new Error('Selected original S25 bed required');\n      if([...entries.values()].filter(e=>e.loaded).length!==13)\n        throw new Error('Exactly 13 source GLBs required');\n      const gl=renderer.getContext(),w=renderer.domElement.width,h=renderer.domElement.height;\n      if(w!==618||h!==768)throw new Error('618x768 S25 WebGL required');\n      const originalTarget=renderer.getRenderTarget(),originalJSON=snapshot(),\n        originalCamera=camera.position.toArray();\n      const grid=floor.children.find(c=>c.userData.roomEditorGuideGrid===true);\n      const gridVisible=grid?.visible,gizmoVisible=transform.visible,materials=[];\n      selected.group.traverse(mesh=>{\n        if(!mesh.isMesh)return;\n        const baseline=s25RawViewBackup.displayBaseByMesh.get(mesh);\n        if(!baseline)throw new Error('Missing S25 original display material');\n        materials.push({mesh,baseline,old:mesh.material});\n      });\n      if(!materials.length||!materials.some(m=>m.old!==m.baseline))\n        throw new Error('Expected selected-bed tint');\n      const rt=new THREE.WebGLRenderTarget(w,h,{samples:0,depthBuffer:true,stencilBuffer:false});\n      rt.texture.colorSpace=renderer.outputColorSpace;\n      const read=(target)=>{\n        const bytes=new Uint8Array(w*h*4);\n        if(target){\n          renderer.setRenderTarget(rt);renderer.clear();renderer.render(scene,camera);\n          renderer.readRenderTargetPixels(rt,0,0,w,h,bytes);\n        }else{\n          renderer.setRenderTarget(null);renderer.render(scene,camera);\n          gl.readPixels(0,0,w,h,gl.RGBA,gl.UNSIGNED_BYTE,bytes);\n        }\n        if(gl.getError()!==gl.NO_ERROR)throw new Error('GPU readPixels error');\n        return bytes;\n      };\n      const compare=(a,b)=>{\n        let sum=0,changed=0,positive=0,negative=0,max=0;\n        for(let i=0;i<a.length;i+=4){\n          let altered=false;\n          for(let k=0;k<3;k++){\n            const d=a[i+k]-b[i+k];sum+=Math.abs(d);\n            max=Math.max(max,Math.abs(d));\n            if(d>0)positive++;else if(d<0)negative++;\n            if(d!==0)altered=true;\n          }\n          if(altered)changed++;\n        }\n        return {rgb_mae:sum/(w*h*3),changed_pixels:changed,\n          changed_fraction:changed/(w*h),max_channel_delta:max,\n          native_brighter_channel_count:positive,\n          offscreen_brighter_channel_count:negative};\n      };\n      let result;\n      try{\n        if(grid)grid.visible=false;\n        transform.visible=false;\n        const tintedNative=read(false),tintedOffscreen=read(true);\n        for(const m of materials)m.mesh.material=m.baseline;\n        const plainNative=read(false),plainOffscreen=read(true);\n        result={selected_id:selected.id,source_models:13,\n          tinted_native_vs_offscreen0:compare(tintedNative,tintedOffscreen),\n          untinted_native_vs_offscreen0:compare(plainNative,plainOffscreen),\n          native_selected_vs_untinted:compare(tintedNative,plainNative),\n          offscreen_selected_vs_untinted:compare(tintedOffscreen,plainOffscreen),\n          target_samples:rt.samples,target_color_space:rt.texture.colorSpace,\n          renderer_output_color_space:renderer.outputColorSpace,\n          drawing_buffer:[w,h],physical_samsung_test:false,\n          visual_acceptance:'NOT_GRANTED'};\n      }finally{\n        for(const m of materials)m.mesh.material=m.old;\n        if(grid)grid.visible=gridVisible;\n        transform.visible=gizmoVisible;\n        renderer.setRenderTarget(originalTarget);renderer.render(scene,camera);\n        rt.dispose();\n      }\n      if(snapshot()!==originalJSON||\n         camera.position.toArray().some((v,i)=>v!==originalCamera[i]))\n        throw new Error('GPU diagnostic modified original source state or camera');\n      return result;\n    },\n";
    const s25CurtainOrderDepthPrecisionProbeInjection="\n    // E2E-ONLY; compare genuine owner-GLB drape depth/order/fragment precision\n    // in identical native S25 618x768 WebGL frames. No exported state changes.\n    s25CurtainOrderDepthPrecisionRasterProbe(){\n      if(!s25RawPreview||[...entries.values()].filter(e=>e.loaded).length!==13)\n        throw new Error('Owner S25 view with 13 original source GLBs required');\n      const w=renderer.domElement.width,h=renderer.domElement.height;\n      if(w!==618||h!==768||!s25RawViewBackup)\n        throw new Error('Exactly 618x768 source S25 frame required');\n      const modelMeshes=[];\n      for(const entry of entries.values())entry.group.traverse(mesh=>{\n        if(mesh.isMesh)modelMeshes.push({id:entry.id,mesh,order:mesh.renderOrder});\n      });\n      if(modelMeshes.length<13)throw new Error('Original source GLB meshes missing');\n      const materials=[...new Set(modelMeshes.flatMap(({mesh})=>\n        Array.isArray(mesh.material)?mesh.material:[mesh.material]))];\n      if(materials.some(m=>!m||!m.isMeshBasicMaterial||m.transparent||\n        !m.depthTest||!m.depthWrite||m.depthFunc!==THREE.LessEqualDepth))\n        throw new Error('Cannot isolate opaque S25 LEQUAL source material states');\n      const sourceValues=materials.map(material=>({\n        material,precision:material.precision,depthFunc:material.depthFunc\n      }));\n      const originalJSON=snapshot(),originalCamera=camera.position.toArray(),\n        originalTarget=orbit.target.toArray(),\n        originalFov=camera.fov,renderTarget=renderer.getRenderTarget();\n      const grid=floor.children.find(c=>c.userData.roomEditorGuideGrid===true);\n      const originalGrid=grid?.visible,originalGizmo=transform.visible;\n      const gl=renderer.getContext(),captures=[];\n      const configure=(order,mediump,less)=>{\n        modelMeshes.forEach(({mesh,order:base},i)=>{\n          mesh.renderOrder=order?1000+i:base;\n        });\n        sourceValues.forEach(({material,precision,depthFunc})=>{\n          const nextPrecision=mediump?'mediump':precision;\n          const nextDepth=less?THREE.LessDepth:depthFunc;\n          if(material.precision!==nextPrecision||material.depthFunc!==nextDepth){\n            material.precision=nextPrecision;\n            material.depthFunc=nextDepth;\n            material.needsUpdate=true;\n          }\n        });\n      };\n      const take=(name)=>{\n        renderer.setRenderTarget(null);\n        renderer.render(scene,camera);\n        const samples=[];\n        for(const [x,y] of [[435,384],[450,267]]){\n          const rgba=new Uint8Array(4);\n          gl.readPixels(x,h-y-1,1,1,gl.RGBA,gl.UNSIGNED_BYTE,rgba);\n          samples.push({xy:[x,y],rgb:Array.from(rgba.slice(0,3))});\n        }\n        if(gl.getError()!==gl.NO_ERROR)throw new Error('WebGL readPixels failed');\n        captures.push({name,samples,png_data_url:renderer.domElement.toDataURL('image/png')});\n      };\n      let restored=false;\n      try{\n        if(grid)grid.visible=false;\n        transform.visible=false;\n        configure(false,false,false);\n        take('three_current_lequal_highp');\n        configure(true,false,false);\n        take('owner_item_mesh_traversal_order_lequal_highp');\n        configure(false,false,true);\n        take('three_order_less_depth_highp');\n        configure(false,true,false);\n        take('three_order_lequal_mediump');\n        configure(true,true,false);\n        take('owner_order_lequal_mediump');\n      }finally{\n        modelMeshes.forEach(({mesh,order})=>{mesh.renderOrder=order;});\n        sourceValues.forEach(({material,precision,depthFunc})=>{\n          if(material.precision!==precision||material.depthFunc!==depthFunc){\n            material.precision=precision;material.depthFunc=depthFunc;material.needsUpdate=true;\n          }\n        });\n        if(grid)grid.visible=originalGrid;\n        transform.visible=originalGizmo;\n        renderer.setRenderTarget(renderTarget);\n        renderer.render(scene,camera);\n        restored=modelMeshes.every(({mesh,order})=>mesh.renderOrder===order)&&\n          sourceValues.every(({material,precision,depthFunc})=>\n            material.precision===precision&&material.depthFunc===depthFunc);\n      }\n      if(!restored||snapshot()!==originalJSON||\n        camera.position.toArray().some((v,i)=>v!==originalCamera[i])||\n        orbit.target.toArray().some((v,i)=>v!==originalTarget[i])||\n        camera.fov!==originalFov)\n        throw new Error('A/B GPU probe did not fully restore owner source state');\n      return {\n        read_only:true,visual_acceptance:'NOT_GRANTED',\n        original_13_glb_mesh_count:modelMeshes.length,\n        source_mesh_order_contract:'owner item order + glTF scene mesh traversal (primitive-order proxy; exact glTF primitive mapping not proven)',\n        model_order_sample:modelMeshes.filter(x=>x.id==='room_window_drapes'||\n          x.id==='room_plant_large').slice(0,8).map(x=>({id:x.id,original_order:x.order})),\n        material_count:materials.length,baseline_depth_func:'LEQUAL',\n        temporary_depth_test:'LESS',temporary_fragment_precision:'mediump',\n        original_camera_m:originalCamera,original_camera_target_m:originalTarget,\n        room_m:{...room},drawing_buffer_px:[w,h],\n        source_materials_and_mesh_orders_restored:restored,captures\n      };\n    },\n";
    const s25CurtainQuantProbeInjection="    s25CurtainQuantizedSourceRasterProbe(){\n      if(!s25RawPreview||[...entries.values()].filter(e=>e.loaded).length!==13)\n        throw new Error('Approved original S25 scene and 13 GLBs required');\n      const group=entries.get('room_window_drapes')?.group,candidates=[];\n      if(!group)throw new Error('Missing original window curtain group');\n      group.traverse(mesh=>{\n        if(!mesh.isMesh)return;\n        const mats=Array.isArray(mesh.material)?mesh.material:[mesh.material];\n        if(mats.some(m=>m?.name==='S25_UNLIT_DISPLAY_material.010'))candidates.push(mesh);\n      });\n      if(candidates.length!==1)throw new Error('Expected one original source curtain primitive');\n      const mesh=candidates[0],geometry=mesh.geometry;\n      const positions=geometry.getAttribute('position'),uvs=geometry.getAttribute('uv');\n      if(positions?.count!==381278||uvs?.count!==381278)\n        throw new Error('Expected 381278 original source curtain vertices and UVs');\n      const mat=Array.isArray(mesh.material)?\n        mesh.material.find(m=>m?.name==='S25_UNLIT_DISPLAY_material.010'):mesh.material;\n      const flags={name:mat.name,transparent:mat.transparent,opacity:mat.opacity,\n        depthWrite:mat.depthWrite,depthTest:mat.depthTest,blending:mat.blending,\n        side:mat.side,renderOrder:mesh.renderOrder,hasTexture:!!mat.map,\n        textureColorSpace:mat.map?.colorSpace??null,flipY:mat.map?.flipY??null};\n      const state=snapshot(),cam=camera.position.toArray(),target=renderer.getRenderTarget();\n      const grid=floor.children.find(c=>c.userData.roomEditorGuideGrid===true);\n      const gridWas=grid?.visible,gizmoWas=transform.visible;\n      const capture=()=>{\n        renderer.setRenderTarget(null);renderer.render(scene,camera);\n        return renderer.domElement.toDataURL('image/png');\n      };\n      const trials=[\n        {name:'position_mm',p:3,uv:null},\n        {name:'uv_1e4',p:null,uv:4},\n        {name:'position_mm_uv_1e4',p:3,uv:4}\n      ];\n      let baseline=null,restored=null;const captures=[];\n      try{\n        if(grid)grid.visible=false;\n        transform.visible=false;\n        baseline=capture();\n        for(const trial of trials){\n          const cloned=geometry.clone(),stats={};\n          try{\n            for(const [attrName,digits] of [['position',trial.p],['uv',trial.uv]]){\n              if(digits===null)continue;\n              const a=cloned.getAttribute(attrName),fac=10**digits;\n              let changed=0,maxDelta=0;\n              for(let i=0;i<a.count;i++)for(let j=0;j<a.itemSize;j++){\n                const value=a.getComponent(i,j),rounded=Math.round(value*fac)/fac;\n                if(!Number.isFinite(rounded))throw new Error('Nonfinite source attribute');\n                if(rounded!==value){\n                  changed++;maxDelta=Math.max(maxDelta,Math.abs(rounded-value));\n                  a.setComponent(i,j,rounded);\n                }\n              }\n              a.needsUpdate=true;\n              stats[attrName]={digits,components_changed:changed,max_delta:maxDelta};\n            }\n            mesh.geometry=cloned;\n            captures.push({name:trial.name,stats,png_data_url:capture()});\n          }finally{\n            mesh.geometry=geometry;\n            cloned.dispose();\n          }\n        }\n        restored=capture();\n      }finally{\n        mesh.geometry=geometry;\n        if(grid)grid.visible=gridWas;\n        transform.visible=gizmoWas;\n        renderer.setRenderTarget(target);renderer.render(scene,camera);\n      }\n      if(mesh.geometry!==geometry||snapshot()!==state||\n         camera.position.toArray().some((x,i)=>x!==cam[i]))\n        throw new Error('Temporary source-view geometry/camera/layout was not restored');\n      return {source_geometry_original:true,original_json_unchanged:true,\n        material_flags:flags,source_vertex_count:positions.count,\n        camera_position_m:cam,room_m:{...room},\n        hypothesis:'Owner offline drape may quantize positions to 0.001m and UV to 0.0001; NOT exact GLB parity',\n        baseline_png_data_url:baseline,restored_png_data_url:restored,captures,\n        visual_acceptance:'NOT_GRANTED'};\n    },\n";
    // V167: PROOF-ONLY, branch-local diagnostic. Never change editor.js or
    // any source GLB/JSON/camera. Take native full 412x915 screenshots outside.
    const s25BackdropAblationInjection=String.raw`
    proofS25BackdropTrialBegin(mode) {
      if(mode!=='source-dark'&&mode!=='backdrop-hidden')
        throw new Error('Unsupported bounded S25 window diagnostic');
      if(window.__proofS25BackdropTrial||!s25RawPreview||!s25RawViewBackup||
         loadCount!==13||failCount)
        throw new Error('S25 original 13-GLB view required; no active trial');
      const entry=entries.get('room_window_drapes'),matches=[];
      if(!entry?.loaded)throw new Error('Original curtain GLB missing');
      entry.group.traverse(mesh=>{
        if(!mesh.isMesh)return;
        const original=sourceDrapeMaterials.get(mesh),mat=mesh.material;
        if(original?.name===NIGHT_BACKDROP_MATERIAL)matches.push({mesh,original,mat});
      });
      if(matches.length!==1)
        throw new Error('Ablation requires exactly one independently isolated backdrop mesh; found '+matches.length);
      const {mesh,original,mat}=matches[0];
      if(Array.isArray(mesh.material)||Array.isArray(original)||!mat?.isMeshBasicMaterial||
         !mesh.visible||!!original.map||original.color.getHexString()!=='1d242b'||
         original.opacity!==1||!!mat.map||mat.color.getHexString()!=='d9c9b3')
        throw new Error('Expected exact source dark material and distinct S25 beige clone');
      const state={
        mesh,mat,visible:mesh.visible,clone:null,original,
        savedLayout:JSON.stringify(snapshot()),room:{...room},
        camera:camera.position.toArray(),target:orbit.target.toArray(),
        fov:camera.fov,aspect:camera.aspect,
        originalHex:original.color.getHexString()
      };
      window.__proofS25BackdropTrial=state;
      try {
        if(mode==='source-dark') {
          state.clone=mat.clone();
          state.clone.name='E2E_ONLY_EXACT_SOURCE_NIGHT_HEX_1d242b';
          state.clone.color.setHex(0x1d242b);
          mesh.material=state.clone;
        } else {
          mesh.visible=false; // Only this separate source-backed opaque mesh.
        }
        renderer.render(scene,camera);
        invalidateMobileFrame();
        return {mode,original_source_hex:state.originalHex,
          shown_hex:mesh.material.color.getHexString(),
          backdrop_mesh_visible:mesh.visible,
          backdrop_source_mesh_material_slots:1,
          original_13_glbs:loadCount,
          room_m:{...room},camera_position_m:camera.position.toArray(),
          source_layout_unchanged:JSON.stringify(snapshot())===state.savedLayout,
          diagnostic_only:true,visual_acceptance:'NOT_GRANTED'};
      } catch(error) {
        this.proofS25BackdropTrialRestore();
        throw error;
      }
    },
    proofS25BackdropTrialRestore() {
      const s=window.__proofS25BackdropTrial;
      if(!s)return {no_active_trial:true};
      s.mesh.material=s.mat;s.mesh.visible=s.visible;
      if(s.clone)s.clone.dispose();
      window.__proofS25BackdropTrial=null;
      renderer.render(scene,camera);
      invalidateMobileFrame();
      const ok=s.mesh.material===s.mat&&s.mesh.visible===s.visible&&
        s.original.color.getHexString()===s.originalHex&&
        JSON.stringify(snapshot())===s.savedLayout&&
        camera.position.toArray().every((v,i)=>v===s.camera[i])&&
        orbit.target.toArray().every((v,i)=>v===s.target[i])&&
        camera.fov===s.fov&&camera.aspect===s.aspect&&
        Object.entries(s.room).every(([k,v])=>room[k]===v)&&
        s25RawPreview&&loadCount===13;
      if(!ok)throw new Error('Temporary backdrop A/B did not restore source/camera/layout');
      return {restored:true,original_source_material_untouched:true,
        original_display_material_identity_restored:true,
        original_13_glbs:loadCount,owner_layout_camera_room_unchanged:true};
    },
`;
    await page.route('**/tools/room-layout-editor/editor.js',async route=>{
      let source=fs.readFileSync(path.join(__dirname,'editor.js'),'utf8');
      const nativeRendererAAAnchor='antialias: !TOUCH_DEVICE';
      assert.equal(source.split(nativeRendererAAAnchor).length,2,
        'Exactly one native renderer AA configuration expected');
      if(NATIVE_AA4_MODE)
        source=source.replace(nativeRendererAAAnchor,'antialias: true');

      // E2E-only reversible render-loop control. Production editor.js is NOT
      // changed; both AA modes are routed through the same measured code.
      const renderStart='renderer.setAnimationLoop(()=>{';
      const renderEnd='    mobileRenderStats.worst_render_ms=Math.max(mobileRenderStats.worst_render_ms,elapsed);\n  }\n});\n\nfunction viewPreset';
      const statsAnchor='const mobileRenderStats = {renders:0, skipped:0, worst_render_ms:0, total_render_ms:0};';
      assert.equal(source.split(renderStart).length,2,'One render-loop start required');
      assert.equal(source.split(renderEnd).length,2,'One render-loop end required');
      assert.equal(source.split(statsAnchor).length,2,'One render-stats declaration required');
      source=source.replace(statsAnchor,statsAnchor+'\nconst __proofRenderFrames=[];');
      source=source.replace(renderStart,'const __proofNativeAnimationLoop=()=>{');
      source=source.replace(renderEnd,
        '    mobileRenderStats.worst_render_ms=Math.max(mobileRenderStats.worst_render_ms,elapsed);\n'+
        '    __proofRenderFrames.push({epoch_ms:performance.timeOrigin+began,duration_ms:elapsed});\n'+
        '    if(__proofRenderFrames.length>500)__proofRenderFrames.shift();\n'+
        '  }\n};\nrenderer.setAnimationLoop(__proofNativeAnimationLoop);\n\nfunction viewPreset');
      const controlledPauseInjection=[
        '    proofMobileRenderLoopPause(paused) {',
        "      if(typeof paused!=='boolean'||!TOUCH_DEVICE)",
        "        throw new Error('Render pause probe requires mobile boolean');",
        "      if([...entries.values()].filter(e=>e.loaded).length!==13)",
        "        throw new Error('13 original GLBs required for CDP probe');",
        '      if(paused)renderer.setAnimationLoop(null);',
        '      else {invalidateMobileFrame();renderer.setAnimationLoop(__proofNativeAnimationLoop);}',
        '      return {paused,render_stats:{...mobileRenderStats}};',
        '    },',
        '    proofMobileRecentFrames() {return __proofRenderFrames.slice();},',
        ''
      ].join('\n');
      const anchor='    mobileRenderStats() {';
      assert.equal(source.split(anchor).length,2,'Single E2E readback injection point required');
      return route.fulfill({body:source.replace(anchor,s25ColorFilterProbeInjection+s25TextureAuditInjection+s25TintProbeInjection+s25CurtainQuantProbeInjection+s25CurtainOrderDepthPrecisionProbeInjection+s25BackdropAblationInjection+controlledPauseInjection+anchor),contentType:'text/javascript'});
    });
    await page.goto('http://127.0.0.1:8765/tools/room-layout-editor/?e2e=1',{waitUntil:'domcontentloaded',timeout:30000});
    await page.waitForFunction(()=>document.querySelector('#loaded')?.textContent?.includes('13/13'),null,{timeout:240000});
    const status=await page.locator('#status').innerText();
    assert.match(status,/13\/13/);assert.match(status,/0 Fehler/);
    assert.equal(await page.locator('#mobile-controls').isVisible(),true);
    assert.equal(await page.locator('#mobile-object option').count(),13);
    assert.equal(await page.locator('#reference-preview').evaluate(e=>e.naturalWidth>0),true);
    assert.equal(await page.locator('canvas').evaluate(c=>!!(c.getContext('webgl2')||c.getContext('webgl'))),true);
    assert.equal(await page.locator('canvas').evaluate(c=>getComputedStyle(c).touchAction),'none');
    // A/B is the ACTUAL interactive WebGL default framebuffer, not the
    // separate offscreen 4x MSAA render target tested previously.
    const nativeAA=await page.evaluate(()=>{
      const canvas=document.querySelector('#viewport canvas');
      const gl=canvas?.getContext('webgl2');
      if(!gl)throw new Error('Native original-GLB WebGL2 canvas unavailable');
      return {
        context_attributes:gl.getContextAttributes(),
        default_framebuffer_samples:gl.getParameter(gl.SAMPLES),
        max_samples:gl.getParameter(gl.MAX_SAMPLES),
        drawing_buffer_px:[gl.drawingBufferWidth,gl.drawingBufferHeight],
        css_viewport_px:[innerWidth,innerHeight],
        renderer_stats_before_touch:window.__celineRoomEditorE2E.mobileRenderStats()
      };
    });
    assert.deepEqual(nativeAA.css_viewport_px,[412,915],
      'Exact S25-like 412x915 mobile viewport remains mandatory');
    assert.equal(nativeAA.context_attributes.antialias,NATIVE_AA4_MODE,
      'Native interactive WebGL AA context attribute mismatch');
    assert.equal(nativeAA.default_framebuffer_samples,NATIVE_AA4_MODE?4:0,
      'Native interactive framebuffer MUST truly use requested 0 or 4 samples');
    fs.writeFileSync(path.join(out,'native-mobile-aa-context.json'),
      JSON.stringify({source_sha:process.env.GITHUB_SHA||null,
        mode:NATIVE_AA4_MODE?'native_4x_msaa':'native_aa0',
        nativeAA,not_physical_samsung:true,visual_acceptance:'NOT_GRANTED'},null,2));
    record.push({name:'NATIVE_INTERACTIVE_MOBILE_AA_CONTEXT_VERIFIED',
      mode:NATIVE_AA4_MODE?'native_4x_msaa':'native_aa0',nativeAA});
    // The specific user-approved S25 JSON is the sole arrangement baseline.
    // Check original source GLB wrappers against the exact exported transforms
    // before touch exercises mutate the selected (unlocked) bed.
    const approvedBytes=fs.readFileSync(path.join(__dirname,'layout-candidates',
      '2026-10-31-s25-user-approved-final-13glb.json'));
    const approvedSha=crypto.createHash('sha256').update(approvedBytes).digest('hex');
    assert.equal(approvedSha,'471a0cbfd267299f878e74783d003dfc808a6fb2c3fc6449e1d62acc7ca7611f');
    const approved=JSON.parse(approvedBytes.toString('utf8'));
    const userAudit=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
    assert.deepEqual(userAudit.room_m,{width:4.4,depth:4.2,height:2.65});
    assert.equal(userAudit.model_count,13);
    assert.equal(userAudit.all_source_fidelity_glbs_loaded,true);
    assert.deepEqual(approved.objects.map(o=>o.id),["room_bed","room_dresser","room_plant_large","room_plant_small","room_floor_lamp","room_nightstand_back","room_nightstand_front","room_lounge_chair","room_rug","room_foreground_table","room_window_drapes","room_wall_shelf_books","room_round_mirror"]);
    for(const expected of approved.objects) {
      const actual=userAudit.objects.find(o=>o.id===expected.id);
      assert(actual?.loaded,'Missing original GLB '+expected.id);
      assert.equal(actual.locked,expected.locked,'User lock '+expected.id);
      for(const key of ['translation_m','rotation_euler_deg','scale_xyz'])
        for(let i=0;i<3;i++)
          assert(Math.abs(actual.transform[key][i]-expected[key][i])<1e-9,
            'User-approved '+expected.id+' '+key+'['+i+'] differs from saved JSON');
    }
    const baseline=await page.evaluate(()=>window.__celineRoomEditorE2E.serializedLayout());
    assert.deepEqual(baseline.objects.map(o=>o.id),approved.objects.map(o=>o.id));
    for(let i=0;i<13;i++)
      for(const key of ['translation_m','rotation_y_deg','rotation_euler_deg',
        'scale_xyz','uniform_scale','locked'])
        assert.deepEqual(baseline.objects[i][key],approved.objects[i][key],
          'Owner exact source field '+approved.objects[i].id+' '+key);
    record.push({name:'OWNER_FINAL_EXACT_SOURCE_FIELDS_BEFORE_TOUCH_PASS'});

    // Preserve the owner's raw source AABBs, including any existing saved
    // out-of-room geometry. Do not normalize or clamp that user-owned scene.
    const ownerBounds={};
    const faces=['left','right','floor','ceiling','back','front'];
    for(const object of userAudit.objects){
      const box=object.conservative_world_aabb_m;
      assert(box&&Array.isArray(box.min)&&Array.isArray(box.max),
        'Missing original owner GLB bounds '+object.id);
      const values=[...box.min,...box.max,object.floor_bottom_y_m,
        ...faces.map(face=>object.overflow_faces_m?.[face])];
      assert.equal(values.length,13);
      assert(values.every(Number.isFinite),'Non-finite owner GLB bounds '+object.id);
      ownerBounds[object.id]={
        min:box.min,max:box.max,
        ground:object.floor_bottom_y_m,
        overflow:Object.fromEntries(faces.map(face=>[face,object.overflow_faces_m[face]]))
      };
    }
    assert.equal(Object.keys(ownerBounds).length,13);
    fs.writeFileSync(path.join(out,'owner-final-source-boundary-diagnostic.json'),
      JSON.stringify({source_sha:process.env.GITHUB_SHA||null,
        approved_layout_sha256:approvedSha,room:approved.room,
        policy:'OWNER_SAVED_SOURCE_BOUNDS_DIAGNOSTIC_NOT_AUTO_CLAMP',
        source_bounds:ownerBounds},null,2));
    record.push({name:'OWNER_FINAL_SOURCE_GEOMETRY_BOUNDS_RECORDED',source_count:13});

    record.push({name:'USER_APPROVED_FINAL_13_ORIGINAL_GLB_IMPORT_PASS',
      layout_sha256:approvedSha,saved_at:approved.saved_at,model_count:13,
      locks:approved.objects.filter(o=>o.locked).length,
      tolerance_absolute_m_or_degree_or_scale:1e-9});
    // Strict original owner-S25 RAW geometry frame, independent of JSON only.
    // Offsets recovered from the actual 99MB standalone original handset HTML.
    const ownerFrames=await page.evaluate(()=>window.__celineRoomEditorE2E.ownerSourceFrameAudit());
    const originalS25Offsets={
      room_bed:[0,0.13571697473526,0],
      room_dresser:[0,0.012040197849273682,0],
      room_plant_large:[0,0.1285751461982727,0],
      room_plant_small:[-0.0000021457672119140625,0.11341476440429688,0],
      room_floor_lamp:[0,0.04433482885360718,0],
      room_nightstand_back:[0,0,0], // offline lookup by instance, not rear GLB stem
      room_nightstand_front:[0,-0.6299548149108887,0],
      room_lounge_chair:[0,0.2647475004196167,0],
      room_rug:[0,0.006009998731315136,0],
      room_foreground_table:[0,-0.13141870498657227,0],
      room_window_drapes:[0,0,0],
      room_wall_shelf_books:[0,0,0],
      room_round_mirror:[0,0,0]
    };
    assert.equal(ownerFrames.model_count,13);
    assert.equal(ownerFrames.all_owner_s25_raw_frames_valid,true,
      '13 real GLBs must have original S25 raw geometry, not their extra source anchors');
    assert.deepEqual(ownerFrames.objects.map(o=>o.id),approved.objects.map(o=>o.id));
    for(const item of ownerFrames.objects){
      assert(item.ignored_original_source_anchor,'Missing source GLB anchor '+item.id);
      assert(item.anchor_is_identity&&item.raw_geometry_offset_exact&&
        item.model_root_not_recentered&&item.other_nodes_identity,
        'Unexpected additional glTF anchor or world center applied to '+item.id);
      assert.equal(item.owner_rotation_order,'ZYX','S25 raw Euler multiplication order '+item.id);
      for(let i=0;i<3;i++)
        assert(Math.abs(item.owner_geometry_offset_m[i]-originalS25Offsets[item.id][i])<1e-5,
          'Original S25 geometry offset mismatch '+item.id+' axis '+i);
    }
    record.push({name:'ORIGINAL_OWNER_S25_RAW_GLB_FRAME_13_OF_13_PASS',
      source_layout_sha256:approvedSha,original_s25_camera:ownerFrames.original_s25_camera,
      geometry_offsets_m:Object.fromEntries(ownerFrames.objects.map(o=>[o.id,o.owner_geometry_offset_m]))});
    const initial=await page.evaluate(()=>window.__celineRoomEditorE2E.cameraProjection());
    const ownerYaw=0.19,ownerPitch=0.42,ownerDistance=6.6;
    const expectedS25Camera=[Math.sin(ownerYaw)*Math.cos(ownerPitch)*ownerDistance,
      0.85+Math.sin(ownerPitch)*ownerDistance,
      Math.cos(ownerYaw)*Math.cos(ownerPitch)*ownerDistance];
    assert.equal(initial.vertical_fov_deg,60,'Original owner S25 vertical FOV');
    assert.deepEqual(initial.camera_target_m,[0,0.85,0]);
    for(let i=0;i<3;i++)
      assert(Math.abs(initial.camera_position_m[i]-expectedS25Camera[i])<1e-5,
        'Original S25 camera coordinate '+i);
    record.push({name:'REAL_MOBILE_VIEWPORT_13_ORIGINAL_GLB_PASS',viewport:[412,915],canvas:initial.canvas_px});
    // Proof-only native CDP causality diagnosis on the freshly loaded, naturally
    // stationary OWNER camera, BEFORE any Playwright orbit/damping gesture.
    // The former post-orbit experiment never passed its camera-settle precondition,
    // while AA4 aborts at an earlier mandatory <5s tap and never reaches it.
    // This non-acceptance diagnostic cannot substitute for strict real UI,
    // Orbit, owner JSON roundtrip or true 412x915 viewport gates below.
    let pauseProbe;
    try{
      pauseProbe=await controlledRenderPauseNativeTouchProbe(page,context);
    }catch(e){
      pauseProbe={diagnostic_error:String(e.stack||e),
        product_acceptance:'NOT_GRANTED_BY_DIAGNOSTIC'};
    }
    fs.writeFileSync(path.join(out,'mobile-render-pause-cdp-causality.json'),
      JSON.stringify({source_sha:process.env.GITHUB_SHA||null,
        mode:NATIVE_AA4_MODE?'native_msaa4':'native_aa0',
        phase:'BEFORE_ORBIT_OR_EDIT_AFTER_EXACT_OWNER_IMPORT',
        pauseProbe},null,2));
    record.push({name:'CONTROLLED_NATIVE_CDP_RENDER_PAUSE_CAUSALITY',
      file:'mobile-render-pause-cdp-causality.json',
      measured:!pauseProbe.diagnostic_error,
      product_acceptance:'NOT_GRANTED'});

    // P1 03-Nov: controlled same-scene native CDP canvas versus passive HTML,
    // WebGL render paused in BOTH controls. Proof-only, no UI/product mutation.
    // Continue the existing mandatory orbit, 13/13, save/reload, and real
    // 412x915 capture even if a differential diagnostic exceeds five seconds.
    let targetComparison=null;
    try {
      targetComparison=await pausedCanvasVsPassiveHtmlNativeProbe(page,context);
    } catch (e) {
      targetComparison={diagnostic_error:String(e.stack||e),
        strict_5s_all_taps:false,product_acceptance:'NOT_GRANTED'};
    }
    fs.writeFileSync(path.join(out,'mobile-paused-input-target-comparison-summary.json'),
      JSON.stringify({source_sha:process.env.GITHUB_SHA||null,
        owner_layout_sha256:approvedSha,
        mode:NATIVE_AA4_MODE?'native_msaa4':'native_aa0',
        comparison:targetComparison},null,2));
    record.push({name:'PAUSED_NATIVE_WEBGL_CANVAS_VS_PASSIVE_HTML_DIFFERENTIAL',
      file:'mobile-paused-webgl-versus-html-native-cdp.json',
      strict_5s_all_taps:targetComparison.strict_5s_all_taps===true,
      diagnostic_error:targetComparison.diagnostic_error||null,
      visual_acceptance:'NOT_GRANTED'});

    // V164 acceptance of the ACTUAL mobile default, not an old opt-in render:
    // verify original S25 source scene, native uncropped 412x915 viewport,
    // immutable owner JSON/camera, and real reversible touchscreen exit.
    // The subsequent neutral/PBR/drape A/B below is deliberately performed
    // after this explicit original-S25-to-neutral exit, not faked as startup.
    const defaultRawAudit=await page.evaluate(()=>
      window.__celineRoomEditorE2E.s25RawDisplayAudit());
    assert.equal(defaultRawAudit.active,true,
      'After 13 authentic GLBs load, mobile must start in owner S25 RAW display');
    const rawBefore=await page.evaluate(()=>({
      layout:window.__celineRoomEditorE2E.serializedLayout(),
      camera:window.__celineRoomEditorE2E.cameraProjection(),
      viewport:[innerWidth,innerHeight],scroll_y:scrollY,
      edit_drawer_open:document.querySelector('#mobile-tools-panel').open
    }));
    assert.deepEqual(rawBefore.layout,baseline,
      'Default original S25 view may not mutate any original saved field');
    assert.deepEqual(rawBefore.viewport,[412,915]);
    assert.equal(rawBefore.scroll_y,0);
    assert.equal(rawBefore.edit_drawer_open,false,
      'Original S25 mobile scene must start with editing drawer closed');
    const defaultRawWebgl=await captureRealWebGL(page,
      'mobile-owner-default-s25-raw-real-webgl.png');
    const rawViewportBytes=await page.screenshot({scale:'css',fullPage:false,timeout:30000});
    const rawViewportSize=[rawViewportBytes.readUInt32BE(16),rawViewportBytes.readUInt32BE(20)];
    assert.deepEqual(rawViewportSize,[412,915],
      'Original S25 default requires an authentic uncropped 412x915 viewport');
    const rawViewportAfter=await page.evaluate(()=>({scroll_y:scrollY,
      layout:window.__celineRoomEditorE2E.serializedLayout(),
      camera:window.__celineRoomEditorE2E.cameraProjection()}));
    assert.equal(rawViewportAfter.scroll_y,0,'Original S25 raw viewport must stay unscrolled');
    assert.deepEqual(rawViewportAfter.layout,rawBefore.layout);
    assert.deepEqual(rawViewportAfter.camera,rawBefore.camera);
    fs.writeFileSync(path.join(out,'mobile-owner-default-s25-raw-viewport-412x915.png'),
      rawViewportBytes);
    record.push({name:'MOBILE_OWNER_S25_RAW_ACTUAL_DEFAULT_412x915_CAPTURED',
      raw_default:true,mode:NATIVE_AA4_MODE?'AA4':'AA0',
      raw_default_display_audit:defaultRawAudit,
      webgl:defaultRawWebgl,
      native_viewport_sha256:crypto.createHash('sha256').update(rawViewportBytes).digest('hex'),
      native_viewport_size:rawViewportSize,owner_json_unchanged:true,camera_unchanged:true,
      visual_acceptance:'REQUIRES_MANUAL_IMAGE_INSPECTION'});
    // V171: The genuine V169 beige/dark/hidden A/B already passed independent
    // image review. Current default intentionally uses source-dark #1d242b.
    // Never replay that obsolete beige-baseline diagnostic on the new product
    // state; instead assert one opaque source-identical COLOR on a DISPLAY
    // clone, unchanged GLB material identity and entire user JSON/camera.
    // Genuine AA0/AA4/AA4-repeat touches still use strict <5000ms gates.
    if(!NATIVE_AA4_MODE) {
      const windowBackdrop=await page.evaluate(()=>
        window.__celineRoomEditorE2E.windowBackdropAudit());
      assert.equal(windowBackdrop.matches.length,1,
        'Exactly one authentic window-background material is required');
      const slot=windowBackdrop.matches[0];
      assert.equal(slot.source_hex,'1d242b',
        'Original immutable GLB background must stay source dark');
      assert.equal(slot.displayed_hex,'1d242b',
        'Current S25 display clone must use exact original dark color');
      assert.equal(slot.original_identity,false,
        'S25 raw mode must clone display material, not mutate source GLB');
      assert.equal(slot.source_opacity,1,'Original source backplane must remain opaque');
      assert.equal(slot.displayed_opacity,1,'Display may not fake an open window');
      assert.deepEqual(await page.evaluate(()=>
        window.__celineRoomEditorE2E.serializedLayout()),rawBefore.layout);
      assert.deepEqual(await page.evaluate(()=>
        window.__celineRoomEditorE2E.cameraProjection()),rawBefore.camera);
      record.push({name:'S25_SOURCE_DARK_DEFAULT_MATERIAL_CLONE_PROOF',
        original_source_hex:slot.source_hex,display_clone_hex:slot.displayed_hex,
        original_source_material_identity_preserved:true,
        source_backplane_opaque:true,original_13_glb_layout_unchanged:true,
        existing_native_412x915_sha256:
          crypto.createHash('sha256').update(rawViewportBytes).digest('hex'),
        previous_genuine_v169_ab_artifact_id:13400057960,
        visual_acceptance:'NOT_GRANTED',strict_touch_lt_5000_ms_gate:'UNCHANGED'});
    }
    const rawExitTapCount=observedMobileTaps.length;
    await observeRealTap(page.locator('[data-mobile-action="s25-display"]'),
      'page.locator(\'[data-mobile-action="s25-display"]\')');
    const rawExitTap=observedMobileTaps[rawExitTapCount];
    assert(rawExitTap?.action_succeeded && rawExitTap.elapsed_ms<5000,
      'S25 raw display must exit using a REAL mobile touch strictly under 5000ms');
    assert.equal((await page.evaluate(()=>
      window.__celineRoomEditorE2E.s25RawDisplayAudit())).active,false,
      'S25 default exit must restore the reversible neutral display');
    assert.deepEqual(await page.evaluate(()=>
      window.__celineRoomEditorE2E.serializedLayout()),baseline,
      'Returning to editor neutral view cannot alter any owner-saved field');
    record.push({name:'MOBILE_S25_RAW_DEFAULT_REAL_TOUCH_REVERSIBLE_PASS',
      actual_tap_ms:rawExitTap.elapsed_ms,strict_threshold_exclusive_ms:5000,
      layout_unchanged:true});

    // Genuine 44px+ view shortcut stays reachable in the 3D stage without
    // opening nested mobile drawers; no layout or camera is changed.
    const quickNeutral=await page.evaluate(()=>{
      const el=document.getElementById('mobile-neutral-shortcut');
      const stage=document.getElementById('viewport').getBoundingClientRect();
      const b=el.getBoundingClientRect();
      const hit=document.elementFromPoint(b.left+b.width/2,b.top+b.height/2);
      return {rect:[b.x,b.y,b.width,b.height],stage:[stage.x,stage.y,stage.width,stage.height],
        visible:getComputedStyle(el).display!=='none',
        hit:hit===el||el.contains(hit)};
    });
    assert(quickNeutral.visible && quickNeutral.hit,
      'Neutral quick-view must be a genuine unoccluded viewport touch target');
    assert(quickNeutral.rect[2]>=44 && quickNeutral.rect[3]>=44,
      'Mobile neutral quick-view must be >=44x44 CSS pixels');
    assert(quickNeutral.rect[1]>=quickNeutral.stage[1] &&
      quickNeutral.rect[1]+quickNeutral.rect[3]<=quickNeutral.stage[1]+quickNeutral.stage[3],
      'Neutral quick-view must stay inside the genuine 3D stage');
    record.push({name:'MOBILE_NEUTRAL_VIEW_SHORTCUT_VISIBLE_WHEN_DRAWER_CLOSED_PASS',quickNeutral});

    // Phone-first framing: real original furniture must remain visible when
    // the extra editing toolbar is closed. Open it using a genuine touch for
    // the existing selection/rotate/scale/undo/redo/JSON acceptance sequence.
    assert.equal(await page.locator('#mobile-tools-panel').evaluate(el=>el.open),false,
      'Mobile editing drawer must start closed for visible original GLBs');
    await observeRealTap(page.locator('#mobile-tools-panel > summary'),"page.locator('#mobile-tools-panel > summary')");
    assert.equal(await page.locator('#mobile-tools-panel').evaluate(el=>el.open),true,
      'Mobile editing tools must genuinely open to touchscreen input');
    record.push({name:'MOBILE_EDIT_DRAWER_OPENS_BY_REAL_TOUCH_PASS'});
    await page.locator('#mobile-object').selectOption('room_bed');
    assert.equal(await page.locator('#object').inputValue(),'room_bed');
    await observeRealTap(page.locator('[data-mobile-action="rotate"]'),"page.locator('[data-mobile-action=\"rotate\"]')");
    assert.equal(await page.locator('#rotate').getAttribute('class'),'active');
    await observeRealTap(page.locator('[data-mobile-action="scale-mode"]'),"page.locator('[data-mobile-action=\"scale-mode\"]')");
    assert.equal(await page.locator('#scale-mode').getAttribute('class'),'active');
    await observeRealTap(page.locator('[data-mobile-action="move"]'),"page.locator('[data-mobile-action=\"move\"]')");
    record.push({name:'MOBILE_TOUCH_MODES_AND_SELECTION_PASS',
      rendering:await page.evaluate(()=>window.__celineRoomEditorE2E.mobileRenderStats())});
    // V44: retain the actual native WebGL baseline and fail closed on touch latency.
    // Previous V42 failure was at compositor screenshot after 13/13 GLBs.
    // Capture the actual framebuffer FIRST, then perform the genuine touch/JSON
    // workflow before attempting the compositor capture. No fake furniture.
    const preflight={source_sha:process.env.GITHUB_SHA||null,
      viewport:[await page.evaluate(()=>innerWidth),await page.evaluate(()=>innerHeight)],
      canvas:initial.canvas_px,status,stage_css:await page.locator('#viewport').boundingBox()};
    fs.writeFileSync(path.join(out,'mobile-capture-preflight.json'),JSON.stringify(preflight,null,2));
    const nativeFirst=await captureRealWebGL(page,'mobile-13-original-glb-before-history-real-webgl.png');
    record.push({name:'MOBILE_NATIVE_REAL_WEBGL_BEFORE_HISTORY',nativeFirst});
    // View-only mobile wall toggle: the same 13 approved GLBs must render
    // unobstructed without ever changing the stored owner-final scene.
    // The wall-hidden PNG is a DIAGNOSTIC perspective, NOT a room-shell PASS.
    const beforeWallToggle=await page.evaluate(()=>window.__celineRoomEditorE2E.serializedLayout());
    assert.equal((await page.evaluate(()=>window.__celineRoomEditorE2E.architectureSurfaceAudit())).walls_visible,true);
    await observeRealTap(page.locator('.mobile-extra > summary'),"page.locator('.mobile-extra > summary')");
    const wallButton=page.locator('[data-mobile-action="toggle-walls"]');
    assert.equal(await wallButton.getAttribute('aria-pressed'),'false');
    await observeRealTap(wallButton,"wallButton");
    assert.equal(await wallButton.getAttribute('aria-pressed'),'true');
    assert.equal((await page.evaluate(()=>window.__celineRoomEditorE2E.architectureSurfaceAudit())).walls_visible,false);
    const wallHiddenPreview=await captureRealWebGL(page,'mobile-owner-final-walls-hidden-diagnostic-webgl.png');
    assert.notEqual(wallHiddenPreview.sha256,nativeFirst.sha256,
      'Wall-only visibility preview must have genuinely different WebGL pixels');
    assert.deepEqual(await page.evaluate(()=>window.__celineRoomEditorE2E.serializedLayout()),beforeWallToggle,
      'Hiding walls may not alter any owner-final furniture transform or JSON metadata');
    await observeRealTap(wallButton,"wallButton");
    assert.equal(await wallButton.getAttribute('aria-pressed'),'false');
    assert.equal((await page.evaluate(()=>window.__celineRoomEditorE2E.architectureSurfaceAudit())).walls_visible,true);
    // Owner-approved S25 geometry is unchanged. Mobile launches in a clearly
    // labeled reversible NEUTRAL editor cutaway, not a source/Android render PASS.
    // Actual touchscreen must still reach the immutable original GLB PBR
    // material/shell view and return losslessly to the mobile cutaway.
    const shellBefore=await page.evaluate(()=>window.__celineRoomEditorE2E.shellPreviewAudit());
    const nightBefore=await page.evaluate(()=>window.__celineRoomEditorE2E.windowBackdropAudit());
    assert.equal(nightBefore.matches.length,1,
      'Owner-final GLB must retain exactly one source dark window backdrop');
    assert.equal(nightBefore.matches[0].original_identity,false,
      'Mobile cutaway must use a reversible displayed CLONE, never modify source');
    const neutralButton=page.locator('[data-mobile-action="neutral-shell"]');
    assert.equal(await neutralButton.getAttribute('aria-pressed'),'true',
      'Mobile startup must show the clearly labeled neutral cutaway mode');
    const neutralWebgl=await captureRealWebGL(page,'mobile-owner-final-neutral-cutaway-webgl.png');
    assert.deepEqual(await page.evaluate(()=>window.__celineRoomEditorE2E.serializedLayout()),beforeWallToggle,
      'Default cutaway may not change any approved owner field');
    // Measure the FIRST actual AA4 gate as well: the previous trace was
    // attached only to restoration, which an earlier slow original-shell tap
    // can prevent us from reaching. Keep genuine tap/actionability and <5s.
    const originalTouchElapsed=await traceOriginalNeutralRestoreActionability(
      page,neutralButton,'original');
    assert(originalTouchElapsed<5000,'Original source shell touch toggle must respond within 5000ms');
    assert.equal(await neutralButton.getAttribute('aria-pressed'),'false');
    const shellDuring=await page.evaluate(()=>window.__celineRoomEditorE2E.shellPreviewAudit());
    assert.deepEqual(shellDuring.room_m,shellBefore.room_m);
    assert.equal(shellDuring.wall_count,shellBefore.wall_count);
    assert.notEqual(shellDuring.wall_hex,shellBefore.wall_hex);
    const nightDuring=await page.evaluate(()=>window.__celineRoomEditorE2E.windowBackdropAudit());
    assert.equal(nightDuring.matches.length,1);
    assert.equal(nightDuring.matches[0].source_hex,nightBefore.matches[0].source_hex,
      'Source GLB dark backdrop must remain byte/source material identical');
    assert.notEqual(nightDuring.matches[0].displayed_hex,nightBefore.matches[0].displayed_hex,
      'Original source and neutral view must display measurably different window materials');
    assert.equal(nightDuring.matches[0].original_identity,true,
      'Switching to original source view must restore genuine GLB material identity');
    const originalPbrWebgl=await captureRealWebGL(page,'mobile-owner-final-original-pbr-shell-diagnostic-webgl.png');
    assert.notEqual(originalPbrWebgl.sha256,neutralWebgl.sha256,
      'Original source/PBR and neutral cutaway must contain distinct genuine WebGL pixels');
    assert.deepEqual(await page.evaluate(()=>window.__celineRoomEditorE2E.serializedLayout()),beforeWallToggle,
      'Original shell toggle must preserve the complete 13-object owner JSON');
    const neutralElapsed=await traceOriginalNeutralRestoreActionability(page,neutralButton);
    assert(neutralElapsed<5000,'Restore mobile neutral cutaway touch must respond within 5000ms');
    assert.equal(await neutralButton.getAttribute('aria-pressed'),'true');
    assert.deepEqual(await page.evaluate(()=>window.__celineRoomEditorE2E.shellPreviewAudit()),shellBefore,
      'Editor neutral mode must be restored exactly');
    assert.deepEqual(await page.evaluate(()=>window.__celineRoomEditorE2E.windowBackdropAudit()),nightBefore,
      'Editor neutral clone and immutable GLB source color must restore exactly');
    record.push({name:'MOBILE_DEFAULT_REVERSIBLE_NEUTRAL_CUTAWAY_TECHNICAL_PROOF',
      neutralWebgl,originalPbrWebgl,
      original_touch_elapsed_ms:originalTouchElapsed,
      neutral_touch_elapsed_ms:neutralElapsed,
      room_geometry_unchanged:true,owner_13_source_fields_unchanged:true,
      source_window_material_identity_recovered:true,
      window_backdrop_neutral:nightBefore,window_backdrop_source:nightDuring,
      visual_acceptance:'NOT_GRANTED'});
    // Owner-final isolated curtain A/B: only display material.010 is toggled.
    // Keep neutral shell, opaque window backdrop, real GLBs, camera and full
    // owner JSON identical. The separate native 412x915 PNGs are real browser
    // compositor captures, not generated reference art or Android evidence.
    if (!NATIVE_AA4_MODE) {
      const readFabric=()=>page.evaluate(()=>({
        layout:window.__celineRoomEditorE2E.serializedLayout(),
        camera:window.__celineRoomEditorE2E.cameraProjection(),
        shell:window.__celineRoomEditorE2E.shellPreviewAudit(),
        backdrop:window.__celineRoomEditorE2E.windowBackdropAudit(),
        fabric:window.__celineRoomEditorE2E.sheerPreviewAudit(),
        room:window.__celineRoomEditorE2E.auditScene().room_m,
        scroll_y:window.scrollY,viewport:[innerWidth,innerHeight]
      }));
      const captureFabricViewport=async filename=>{
        const before=await readFabric();
        assert.deepEqual(before.viewport,[412,915],
          'Curtain A/B must use real 412x915 native mobile CSS viewport');
        assert.equal(before.scroll_y,0,
          'Curtain A/B must not photograph scrolled form controls');
        const bytes=await page.screenshot({scale:'css',fullPage:false,timeout:30000});
        assert.deepEqual([bytes.readUInt32BE(16),bytes.readUInt32BE(20)],[412,915],
          'Curtain A/B requires unmodified full native viewport, not a crop');
        fs.writeFileSync(path.join(out,filename),bytes);
        return {filename,pixels:[412,915],bytes:bytes.length,
          sha256:crypto.createHash('sha256').update(bytes).digest('hex')};
      };
      const toggleFabric=checked=>page.evaluate(value=>{
        const control=document.getElementById('sheer-preview');
        control.checked=value;
        control.dispatchEvent(new Event('change',{bubbles:true}));
      },checked);
      const beforeFabric=await readFabric();
      assert.equal(beforeFabric.fabric.controlChecked,true,
        'Expected original warm curtain preview ON in neutral mobile default');
      assert.equal(beforeFabric.fabric.clonedSheerSlots,1,
        'Exactly one mapped original curtain source slot may be preview-cloned');
      assert.equal(beforeFabric.backdrop.matches.length,1);
      const onWebgl=await captureRealWebGL(page,
        'owner-final-neutral-aa0-fabric-on-real-webgl.png');
      const onViewport=await captureFabricViewport(
        'owner-final-neutral-aa0-fabric-on-viewport-412x915.png');
      let offFabric,offWebgl,offViewport;
      try {
        await toggleFabric(false);
        offFabric=await readFabric();
        assert.equal(offFabric.fabric.controlChecked,false);
        assert.equal(offFabric.fabric.clonedSheerSlots,0,
          'Fabric OFF must restore the source material, not a substitute clone');
        const originalSheer=offFabric.fabric.slots.filter(s=>s.selectedSheer);
        assert.equal(originalSheer.length,1);
        assert.equal(originalSheer[0].originalIdentity,true,
          'Fabric OFF must restore exact original material.010 instance');
        offWebgl=await captureRealWebGL(page,
          'owner-final-neutral-aa0-fabric-off-real-webgl.png');
        offViewport=await captureFabricViewport(
          'owner-final-neutral-aa0-fabric-off-viewport-412x915.png');
      } finally {
        // Even if capture/assertion fails, put the original mobile default
        // back before proceeding. No source GLB material is ever rewritten.
        await toggleFabric(true);
      }
      const afterFabric=await readFabric();
      const restoredWebgl=await captureRealWebGL(page,
        'owner-final-neutral-aa0-fabric-restored-real-webgl.png');
      const pair={source_sha:process.env.GITHUB_SHA||null,
        source_owner_json_sha256:approvedSha,
        mode:'CURRENT_OWNER_FINAL_AA0_NEUTRAL_FIXED_SHELL_AND_CAMERA',
        experimental_toggle:'mapped material.010 display-only clone ON/OFF',
        no_furniture_or_camera_or_backdrop_changes:true,
        before:beforeFabric,off:offFabric,restored:afterFabric,
        onWebgl,offWebgl,restoredWebgl,onViewport,offViewport,
        physical_s25_test:false,visual_acceptance:'NOT_GRANTED_BY_AUTOMATED_PROOF'};
      fs.writeFileSync(path.join(out,'mobile-owner-final-curtain-fabric-isolated-pair.json'),
        JSON.stringify(pair,null,2));
      assert.deepEqual(offFabric.layout,beforeFabric.layout,
        'Fabric toggle OFF changed owner 13 furniture TRS/legacy fields or locks');
      assert.deepEqual(afterFabric.layout,beforeFabric.layout,
        'Fabric restore changed owner JSON');
      assert.deepEqual(offFabric.camera,beforeFabric.camera,
        'Curtain ON/OFF altered camera');
      assert.deepEqual(afterFabric.camera,beforeFabric.camera,
        'Curtain restore altered camera');
      assert.deepEqual(offFabric.shell,beforeFabric.shell,
        'Fabric ON/OFF changed neutral room shell');
      assert.deepEqual(afterFabric.shell,beforeFabric.shell,
        'Fabric restore changed room shell');
      assert.deepEqual(offFabric.backdrop,beforeFabric.backdrop,
        'Fabric ON/OFF changed separately opaque window background');
      assert.deepEqual(afterFabric.backdrop,beforeFabric.backdrop,
        'Fabric restore changed window backdrop');
      assert.deepEqual(offFabric.room,beforeFabric.room);
      assert.deepEqual(afterFabric.room,beforeFabric.room);
      assert.deepEqual(afterFabric.fabric,beforeFabric.fabric,
        'Fabric ON restoration must recover the original display-only clone state');
      assert.notEqual(onWebgl.sha256,offWebgl.sha256,
        'ON/OFF must produce visible actual-WebGL fabric difference');
      assert.equal(onWebgl.sha256,restoredWebgl.sha256,
        'Restored original display-only fabric must reproduce original WebGL pixels');
      record.push({name:'OWNER_FINAL_NEUTRAL_AA0_FABRIC_ONLY_REAL_WEBGL_AND_412X915_PAIR',
        onWebgl,offWebgl,restoredWebgl,onViewport,offViewport,
        original_source_material_recovered_on_off:true,
        exact_owner_layout_camera_shell_backdrop_unchanged:true,
        visual_acceptance:'REQUIRES_MANUAL_FRAME_REVIEW'});
    }
    const s25Button=page.locator('[data-mobile-action="s25-display"]');
    const beforeS25=await page.evaluate(()=>
      window.__celineRoomEditorE2E.s25RawDisplayAudit());
    assert.equal(beforeS25.active,false);
    const s25TouchMs=await traceOriginalNeutralRestoreActionability(
      page,s25Button,'s25-enter','[data-mobile-action="s25-display"]','s25Button');
    assert(s25TouchMs<5000,'Genuine S25 display toggle must remain below 5000ms');
    assert.equal(await s25Button.getAttribute('aria-pressed'),'true');
    const duringS25=await page.evaluate(()=>
      window.__celineRoomEditorE2E.s25RawDisplayAudit());
    assert.equal(duringS25.active,true);
    assert.equal(duringS25.original_glbs_loaded,13);
    assert.equal(duringS25.wall_count,4);
    assert.equal(duringS25.visible_wall_count,3);
    assert.equal(duringS25.ceiling_visible,false);
    assert.equal(duringS25.background_hex,'131b21');
    const originalS25FloorHex=await page.evaluate(()=>
      window.__celineRoomEditorE2E.architectureSurfaceAudit().ground_hex);
    assert.equal(originalS25FloorHex,'594f45',
      'Original S25 offline floor tint (.35,.31,.27) must round to RGB(89,79,69)');
    assert(duringS25.unlit_slots>=13,'13 real GLBs must have genuine unlit source slots');
    assert(duringS25.unlit_textured_slots>0,'Must preserve real source GLB color texture');
    assert.deepEqual(duringS25.room_m,beforeS25.room_m);
    assert.deepEqual(duringS25.camera_position_m,beforeS25.camera_position_m);
    assert.equal(duringS25.camera_fov,beforeS25.camera_fov);
    const s25RawFrame=await captureRealWebGL(page,
      'mobile-owner-original-s25-unlit-preview-webgl.png');
    assert.notEqual(s25RawFrame.sha256,nativeFirst.sha256,
      'S25 source-raster view must generate genuinely changed WebGL pixels');
    assert.deepEqual(await page.evaluate(()=>
      window.__celineRoomEditorE2E.serializedLayout()),beforeWallToggle,
      'S25 view must not alter any saved owner field');
    // Original map state plus four REAL GPU pixels, independent of visual acceptance.
    const s25TextureGpu=await page.evaluate(()=>window.__celineRoomEditorE2E.s25CurtainTextureRuntimeAudit());
    assert.equal(s25TextureGpu.read_only,true);
    assert.equal(s25TextureGpu.samples.length,4);
    assert.deepEqual(s25TextureGpu.texture.image_size_px,[1024,1024]);
    fs.writeFileSync(path.join(out,'mobile-owner-s25-curtain-texture-runtime.json'),
      JSON.stringify({source_sha:process.env.GITHUB_SHA||null,
        e2e_response_instrumentation_only:true,original_editor_blob_sha:'aeb8656925bf272a5d0a38abbc627a79ea168066',
        visual_acceptance:'NOT_GRANTED',s25TextureGpu},null,2));
    record.push({name:'S25_CURTAIN_RUNTIME_GPU_TEXTURE_UV_READ_ONLY',
      file:'mobile-owner-s25-curtain-texture-runtime.json',
      texture:s25TextureGpu.texture,samples:s25TextureGpu.samples,
      visual_acceptance:'NOT_GRANTED'});
    // Source-only read-only S25 texture/geometry raster diagnostic.
    const savedBeforeCurtainProbe=await page.evaluate(()=>
      window.__celineRoomEditorE2E.serializedLayout());
    try{
      const q=await page.evaluate(()=>
        window.__celineRoomEditorE2E.s25CurtainQuantizedSourceRasterProbe());
      const savePng=(name,url)=>{
        assert(url.startsWith('data:image/png;base64,'),'Authentic WebGL PNG required');
        const bytes=Buffer.from(url.slice('data:image/png;base64,'.length),'base64');
        assert.deepEqual([bytes.readUInt32BE(16),bytes.readUInt32BE(20)],[618,768]);
        const filename='owner-s25-curtain-'+name+'-618x768.png';
        fs.writeFileSync(path.join(out,filename),bytes);
        return {filename,sha256:crypto.createHash('sha256').update(bytes).digest('hex')};
      };
      const baseline=savePng('original-unmodified',q.baseline_png_data_url);
      const restored=savePng('restored-unmodified',q.restored_png_data_url);
      assert.equal(restored.sha256,baseline.sha256,
        'Temporary curtain geometry rounding must restore identical native raster');
      const captures=q.captures.map(c=>({
        name:c.name,stats:c.stats,...savePng(c.name,c.png_data_url)
      }));
      fs.writeFileSync(path.join(out,'owner-s25-curtain-source-precision-pilot.json'),
        JSON.stringify({source_sha:process.env.GITHUB_SHA||null,owner_sha256:approvedSha,
          original_s25_reference_frame_sha:s25RawFrame.sha256,
          baseline,restored,captures,material_flags:q.material_flags,
          source_vertex_count:q.source_vertex_count,camera_position_m:q.camera_position_m,
          room_m:q.room_m,hypothesis:q.hypothesis,
          owner_json_unchanged:q.original_json_unchanged,
          visual_acceptance:'NOT_GRANTED'},null,2));
      record.push({name:'READ_ONLY_S25_CURTAIN_ROUNDING_RASTER_PILOT',
        file:'owner-s25-curtain-source-precision-pilot.json',baseline,restored,
        captures,visual_acceptance:'NOT_GRANTED'});
    }catch(err){
      record.push({name:'CURTAIN_SOURCE_PRECISION_PILOT_UNAVAILABLE',
        error:String(err.stack||err),visual_acceptance:'NOT_GRANTED'});
    }finally{
      assert.deepEqual(await page.evaluate(()=>
        window.__celineRoomEditorE2E.serializedLayout()),savedBeforeCurtainProbe,
        'Source quantization pilot must leave every original owner field untouched');
    }

    // Read-only native WebGL A/B diagnostics at the locked owner S25 camera.
    // Optional diagnostics cannot bypass genuine touchscreen, JSON, or viewport gates.
    const beforeOrderDepth=await page.evaluate(()=>
      window.__celineRoomEditorE2E.serializedLayout());
    try{
      const probe=await page.evaluate(()=>
        window.__celineRoomEditorE2E.s25CurtainOrderDepthPrecisionRasterProbe());
      assert.deepEqual(probe.drawing_buffer_px,[618,768]);
      assert.equal(probe.original_13_glb_mesh_count>=13,true);
      assert.equal(probe.source_materials_and_mesh_orders_restored,true);
      const captures=[];
      for(const shot of probe.captures){
        const png=Buffer.from(shot.png_data_url.split(',')[1],'base64');
        assert.equal(png.subarray(1,4).toString('ascii'),'PNG');
        assert.deepEqual([png.readUInt32BE(16),png.readUInt32BE(20)],[618,768]);
        const filename='owner-s25-drape-order-depth-'+shot.name+'-618x768.png';
        fs.writeFileSync(path.join(out,filename),png);
        captures.push({name:shot.name,filename,samples:shot.samples,
          sha256:crypto.createHash('sha256').update(png).digest('hex')});
      }
      assert.equal(captures.length,5,'All five unaltered-camera A/B frames required');
      fs.writeFileSync(path.join(out,'owner-s25-drape-order-depth-precision.json'),
        JSON.stringify({...probe,captures,png_data_urls:undefined,
          source_sha:process.env.GITHUB_SHA||null,
          owner_layout_sha256:approvedSha,
          original_camera_frame_sha256:s25RawFrame.sha256,
          capture_phase:'BEFORE_MOBILE_ORBIT_OR_EDIT',
          visual_acceptance:'NOT_GRANTED'},(key,value)=>
            key==='png_data_url'?undefined:value,2));
      record.push({name:'READ_ONLY_OWNER_DRAPE_DRAW_ORDER_DEPTH_MEDIUMP_GPU_AB',
        file:'owner-s25-drape-order-depth-precision.json',
        captures,visual_acceptance:'NOT_GRANTED'});
    }catch(error){
      record.push({name:'DRAPE_ORDER_DEPTH_PRECISION_AB_UNAVAILABLE',
        error:String(error.stack||error),visual_acceptance:'NOT_GRANTED'});
    }finally{
      assert.deepEqual(await page.evaluate(()=>
        window.__celineRoomEditorE2E.serializedLayout()),beforeOrderDepth,
        'Depth/order/precision probe must preserve every owner JSON field');
    }


    // Original offline RGBA8 gamma-filtering versus Three.js sRGB8 filtering.
    // This E2E-only read-only experiment cannot replace mobile/JSON gates.
    const beforeColorFilterProbe=await page.evaluate(()=>
      window.__celineRoomEditorE2E.serializedLayout());
    try{
      const p=await page.evaluate(()=>
        window.__celineRoomEditorE2E.s25CurtainOwnerRawTextureFilterRasterProbe());
      assert.equal(p.source_glbs,13);
      assert.equal(p.source_material_identity_restored,true);
      assert.deepEqual(p.drawing_buffer_px,[618,768]);
      assert.equal(p.captures.length,2);
      const captures=[];
      for(const shot of p.captures){
        const b=Buffer.from(shot.png_data_url.split(',')[1],'base64');
        assert.equal(b.subarray(1,4).toString('ascii'),'PNG');
        assert.deepEqual([b.readUInt32BE(16),b.readUInt32BE(20)],[618,768]);
        const filename='owner-s25-drape-color-filter-'+shot.name+'-618x768.png';
        fs.writeFileSync(path.join(out,filename),b);
        captures.push({name:shot.name,filename,pixels:shot.pixels,
          sha256:crypto.createHash('sha256').update(b).digest('hex')});
      }
      fs.writeFileSync(path.join(out,'owner-s25-drape-color-filter-source-parity.json'),
        JSON.stringify({...p,captures,
          source_sha:process.env.GITHUB_SHA||null,
          owner_layout_sha256:approvedSha,
          capture_phase:'BEFORE_MOBILE_ORBIT_OR_EDIT',
          visual_acceptance:'NOT_GRANTED'},(key,value)=>
          key==='png_data_url'?undefined:value,2));
      record.push({name:'OWNER_RGBA8_VS_THREE_SRGB8_REAL_GPU_TEXTURE_FILTER_AB',
        file:'owner-s25-drape-color-filter-source-parity.json',
        captures,visual_acceptance:'NOT_GRANTED'});
    }catch(error){
      record.push({name:'OWNER_RGBA8_VS_THREE_SRGB8_GPU_AB_UNAVAILABLE',
        error:String(error.stack||error),visual_acceptance:'NOT_GRANTED'});
    }finally{
      assert.deepEqual(await page.evaluate(()=>
        window.__celineRoomEditorE2E.serializedLayout()),beforeColorFilterProbe,
        'Color space probe must preserve every owner JSON field');
    }

    // Capture real 13-owner-GLB AA pair at S25 original camera BEFORE touch orbit.
    // The diagnostic remains optional and cannot bypass later strict gates.
    const layoutBeforeAA=await page.evaluate(()=>
      window.__celineRoomEditorE2E.serializedLayout());
    try{
      const aa=await page.evaluate(()=>
        window.__celineRoomEditorE2E.proofRoomMSAAReadback());
      assert.equal(aa.original_s25_unlit_display,true);
      assert.equal(aa.clean_editing_aids_for_capture,true);
      assert.deepEqual(aa.camera_position_m,duringS25.camera_position_m,
        'MSAA pair must preserve original S25 camera before touchscreen orbit');
      assert.equal(aa.model_count,13);
      assert.deepEqual(aa.room_m,{width:4.4,depth:4.2,height:2.65});
      const captures=[];
      for(const result of aa.captures){
        const png=Buffer.from(result.png_data_url.split(',')[1],'base64');
        assert.equal(png.subarray(1,4).toString('ascii'),'PNG');
        const filename='owner-13glb-s25-msaa'+result.requested_samples+'-618x768.png';
        fs.writeFileSync(path.join(out,filename),png);
        assert.equal(result.actual_samples,result.requested_samples,
          'Actual WebGL framebuffer sample count must match the request');
        captures.push({filename,requested_samples:result.requested_samples,
          actual_samples:result.actual_samples,pixels:[result.width,result.height],
          sha256:crypto.createHash('sha256').update(png).digest('hex')});
      }
      fs.writeFileSync(path.join(out,'owner-13glb-msaa0-msaa4.json'),
        JSON.stringify({source_sha:process.env.GITHUB_SHA||null,
          owner_layout_sha256:approvedSha,captures,max_samples:aa.max_samples,
          original_camera_position_m:aa.camera_position_m,
          matching_clean_s25_frame_sha256:s25RawFrame.sha256,
          capture_phase:'BEFORE_NUDGE_UNDO_REDO_OR_ORBIT',
          edit_guides_hidden_for_both:true,
          physical_s25_aa:'NOT_MEASURED',visual_acceptance:'NOT_GRANTED'},null,2));
      record.push({name:'REAL_OWNER_13GLB_MSAA0_MSAA4_BEFORE_TOUCH_DIAGNOSTIC',
        file:'owner-13glb-msaa0-msaa4.json',captures,
        source_frame:s25RawFrame.filename,visual_acceptance:'NOT_GRANTED'});
    }catch(error){
      record.push({name:'REAL_OWNER_13GLB_MSAA_DIAGNOSTIC_UNAVAILABLE',
        error:String(error.stack||error),visual_acceptance:'NOT_GRANTED'});
    }finally{
      assert.deepEqual(await page.evaluate(()=>
        window.__celineRoomEditorE2E.serializedLayout()),layoutBeforeAA,
        'Real GLB AA proof must preserve exact original fields');
    }

    try{
      const parity=await page.evaluate(()=>
        window.__celineRoomEditorE2E.selectedTintFramebufferAudit());
      assert.equal(parity.selected_id,'room_bed');
      assert.deepEqual(parity.drawing_buffer,[618,768]);
      fs.writeFileSync(path.join(out,'mobile-selected-tint-fbo-parity.json'),
        JSON.stringify({source_sha:process.env.GITHUB_SHA||null,
          owner_json_sha256:approvedSha,parity,
          visual_acceptance:'NOT_GRANTED'},null,2));
      record.push({name:'SELECTED_S25_BED_TINT_VS_NATIVE_FBO_0X_DIAGNOSTIC',
        file:'mobile-selected-tint-fbo-parity.json',
        tinted:parity.tinted_native_vs_offscreen0,
        untinted:parity.untinted_native_vs_offscreen0,
        visual_acceptance:'NOT_GRANTED'});
    }catch(error){
      record.push({name:'SELECTED_TINT_FBO_DIAGNOSTIC_UNAVAILABLE',
        error:String(error.stack||error),visual_acceptance:'NOT_GRANTED'});
    }
    await observeRealTap(s25Button,"s25Button");
    assert.equal(await s25Button.getAttribute('aria-pressed'),'false');
    assert.deepEqual(await page.evaluate(()=>
      window.__celineRoomEditorE2E.s25RawDisplayAudit()),beforeS25,
      'Must restore exact original PBR material and shell display state');
    assert.deepEqual(await page.evaluate(()=>
      window.__celineRoomEditorE2E.serializedLayout()),beforeWallToggle,
      'S25 preview must preserve all 13 object fields and six locks');
    record.push({name:'MOBILE_GENUINE_S25_UNLIT_SOURCE_VIEW_TECHNICAL_PROOF',
      s25TouchMs,s25RawFrame,duringS25,originalS25FloorHex,
      source_json_unchanged:true,source_materials_restored:true,
      visual_acceptance:'NOT_GRANTED'});
    await observeRealTap(page.locator('.mobile-extra > summary'),"page.locator('.mobile-extra > summary')");
    assert.deepEqual(await page.evaluate(()=>window.__celineRoomEditorE2E.serializedLayout()),beforeWallToggle);
    record.push({name:'MOBILE_VIEW_ONLY_WALL_VISIBILITY_TOUCH_PASS',
      original_layout_sha256:approvedSha,hidden_view_diagnostic_only:wallHiddenPreview,
      room_shell_geometry_unchanged:true,original_13_transform_fields_unchanged:true});
    fs.writeFileSync(path.join(out,'mobile-capture-preflight.json'),JSON.stringify({...preflight,nativeFirst},null,2));
    const baseX=Number(await page.locator('#x').inputValue());
    await observeNudgeTapWithHitTest(page,page.locator('[data-nudge="x:1"]'),"page.locator('[data-nudge=\"x:1\"]')");
    const movedX=Number(await page.locator('#x').inputValue());
    assert(movedX>baseX);
    await observeRealTap(page.locator('#mobile-undo'),"page.locator('#mobile-undo')");
    assert(Math.abs(Number(await page.locator('#x').inputValue())-baseX)<0.003);
    // The prior failed proof used a synthesized MOUSE click at mobile-redo;
    // Playwright stalled during that dispatch, not during visibility/stability.
    // Prove the actual touchscreen target before dispatch and require touch events.
    const redoHit=await page.locator('#mobile-redo').evaluate(el=>{
      const box=el.getBoundingClientRect(), px=box.left+box.width/2, py=box.top+box.height/2;
      const hit=document.elementFromPoint(px,py);
      window.__celineRedoTouchEvidence={touchstart:0,pointerdown:0,click:0,
        events:[],after_click_microtask_ms:null};
      for(const type of ['touchstart','pointerdown','click'])
        el.addEventListener(type,()=>{
          const evidence=window.__celineRedoTouchEvidence;
          evidence[type]++;
          evidence.events.push({type,performance_ms:performance.now()});
          if(type==='click')queueMicrotask(()=>{evidence.after_click_microtask_ms=performance.now();});
        },{capture:true});
      return {button_id:el.id,hit_id:hit?.id||null,
        hit_inside_redo:hit===el||el.contains(hit),visible:box.width>0&&box.height>0,
        disabled:el.disabled,box:[box.x,box.y,box.width,box.height]};
    });
    record.push({name:'REDO_TRUE_TOUCH_HIT_TEST',redoHit});
    fs.writeFileSync(path.join(out,'mobile-redo-preflight.json'),JSON.stringify({source_sha:process.env.GITHUB_SHA||null,redoHit,record},null,2));
    assert(redoHit.hit_inside_redo,'mobile-redo center obscured by overlay');
    assert.equal(redoHit.disabled,false,'redo must be enabled after undo');
    const beforeRedoRendering=await page.evaluate(()=>window.__celineRoomEditorE2E.mobileRenderStats());
    const redoPageStarted=await page.evaluate(()=>performance.now());
    const redoSession=NATIVE_AA4_MODE?await context.newCDPSession(page):null;
    const redoFocus=redoSession?await beginFocusedSchedulingTrace(redoSession,'aa4-redo'):null;
    if(redoFocus)await page.evaluate(()=>
      performance.mark('CELINE_E2E_AA4_REDO_BEFORE')).catch(()=>{});
    // Measure only the genuine Playwright tap; trace finalization belongs to
    // diagnostic overhead and must never push a sub-5s tap beyond the strict gate.
    const redoStarted=Date.now();
    let redoElapsed=NaN;
    let redoTraceCleanupMs=0;
    try{
      await observeRealTap(page.locator('#mobile-redo'),"page.locator('#mobile-redo')");
    }finally{
      // Freeze elapsed time immediately on tap settlement, including failures.
      redoElapsed=Date.now()-redoStarted;
      const redoCleanupStarted=Date.now();
      try{
        if(redoFocus) {
          await page.evaluate(()=>
            performance.mark('CELINE_E2E_AA4_REDO_AFTER')).catch(()=>{});
          await finishFocusedSchedulingTrace(redoFocus);
          await redoSession.detach().catch(()=>{});
        }
      }finally{
        redoTraceCleanupMs=Date.now()-redoCleanupStarted;
      }
    }
    const redoTouch=await page.evaluate(()=>window.__celineRedoTouchEvidence);
    const redoPageCompleted=await page.evaluate(()=>performance.now());
    assert(redoTouch.touchstart>=1&&redoTouch.pointerdown>=1&&redoTouch.click>=1,
      'mobile redo must receive genuine touch/pointer/click events');
    const afterRedoRendering=await page.evaluate(()=>window.__celineRoomEditorE2E.mobileRenderStats());
    const redoDiagnostic={name:'GENUINE_MOBILE_REDO_TOUCH_TIMING_DIAGNOSTIC',
      elapsed_ms:redoElapsed,trace_cleanup_ms:redoTraceCleanupMs,
      page_elapsed_ms:redoPageCompleted-redoPageStarted,
      page_start_ms:redoPageStarted,page_end_ms:redoPageCompleted,redoTouch,
      render_before:beforeRedoRendering,render_after:afterRedoRendering};
    record.push(redoDiagnostic);
    fs.writeFileSync(path.join(out,'mobile-redo-timing.json'),
      JSON.stringify({source_sha:process.env.GITHUB_SHA||null,redoDiagnostic},null,2));
    // Diagnostic ONLY after the unchanged real 5-second redo gate has failed.
    // This independent native CDP tap cannot replace Playwright's actual user
    // redo/lock/orbit checks, and never makes the strict gate green.
    if(redoElapsed>=5000){
      const beforeDiagnosticLayout=await page.evaluate(()=>
        window.__celineRoomEditorE2E.serializedLayout());
      const postGate={name:'NATIVE_AA4_PRE_POINTERDOWN_STALL_ISOLATION',
        redo_elapsed_ms:redoElapsed,
        redo_pre_pointerdown_ms:redoTouch.events.find(e=>e.type==='pointerdown')?.performance_ms-redoPageStarted,
        native_aa4:NATIVE_AA4_MODE,real_redo_gate:'FAIL_UNCHANGED'};
      try{
        const hit=await page.evaluate(()=>{
          const canvas=document.querySelector('#viewport canvas');
          const b=canvas.getBoundingClientRect();
          const x=Math.round(b.x+b.width*.53),y=Math.round(b.y+b.height*.30);
          if(document.elementFromPoint(x,y)!==canvas)
            throw new Error('Independent CDP control must target unobstructed canvas');
          window.__celineDirectTouchControl={touchstart:[],pointerdown:[]};
          for(const type of ['touchstart','pointerdown'])
            document.addEventListener(type,e=>{
              if(e.target===canvas)
                window.__celineDirectTouchControl[type].push(performance.now());
            },{capture:true,once:true});
          return {x,y};
        });
        const session=await context.newCDPSession(page);
        try{
          const sent=Date.now();
          await session.send('Input.dispatchTouchEvent',{
            type:'touchStart',touchPoints:[{x:hit.x,y:hit.y,id:12}]});
          const startAck=Date.now();
          await session.send('Input.dispatchTouchEvent',{
            type:'touchEnd',touchPoints:[]});
          const endAck=Date.now();
          postGate.native_cdp_control={hit,send_to_start_ack_ms:startAck-sent,
            start_to_end_ack_ms:endAck-startAck,total_dispatch_ms:endAck-sent,
            page_events:await page.evaluate(()=>window.__celineDirectTouchControl)};
        }finally{await session.detach();}
      }catch(error){
        postGate.native_cdp_control={diagnostic_error:String(error.stack||error)};
      }
      const actionabilityStarted=Date.now();
      try{
        // trial:true performs Playwright actionability and scrolling checks but
        // does NOT send a touch or change the stored user layout.
        await page.locator('#mobile-lock').tap({trial:true,timeout:7000});
        postGate.playwright_actionability_trial={
          elapsed_ms:Date.now()-actionabilityStarted,verdict:'ACTIONABLE_NO_TOUCH'};
      }catch(error){
        postGate.playwright_actionability_trial={
          elapsed_ms:Date.now()-actionabilityStarted,error:String(error)};
      }
      postGate.saved_layout_unchanged=
        (await page.evaluate(()=>window.__celineRoomEditorE2E.serializedLayout()))===
        beforeDiagnosticLayout;
      fs.writeFileSync(path.join(out,'mobile-aa4-pre-pointerdown-isolation.json'),
        JSON.stringify({source_sha:process.env.GITHUB_SHA||null,postGate},null,2));
      record.push(postGate);
    }
    assert(redoElapsed<5000,'Genuine mobile redo remains too slow for interactive touch (<5s required)');
    assert(Math.abs(Number(await page.locator('#x').inputValue())-movedX)<0.003);
    const lockHit=await page.locator('#mobile-lock').evaluate(el=>{
      const box=el.getBoundingClientRect(),x=box.left+box.width/2,y=box.top+box.height/2;
      const hit=document.elementFromPoint(x,y);
      window.__celineLockTouchEvidence={touchstart:0,pointerdown:0,click:0,events:[]};
      for(const type of ['touchstart','pointerdown','click'])
        el.addEventListener(type,()=>{
          window.__celineLockTouchEvidence[type]++;
          window.__celineLockTouchEvidence.events.push({type,performance_ms:performance.now()});
        },{capture:true});
      return {hit_tag:hit?.tagName||null,hit_id:hit?.id||null,
        hit_inside_lock:hit===el||el.contains(hit),disabled:el.disabled,
        box:[box.x,box.y,box.width,box.height]};
    });
    record.push({name:'LOCK_TRUE_TOUCH_PREFLIGHT',lockHit});
    fs.writeFileSync(path.join(out,'mobile-lock-preflight.json'),
      JSON.stringify({source_sha:process.env.GITHUB_SHA||null,lockHit,record},null,2));
    assert(lockHit.hit_inside_lock && !lockHit.disabled,
      'Mobile lock touch center obstructed or button disabled');
    const renderingBeforeLock=await page.evaluate(()=>window.__celineRoomEditorE2E.mobileRenderStats());
    const lockStarted=Date.now();
    await observeRealTap(page.locator('#mobile-lock'),"page.locator('#mobile-lock')");
    const lockTouch=await page.evaluate(()=>window.__celineLockTouchEvidence);
    const lockElapsed=Date.now()-lockStarted;
    record.push({name:'GENUINE_MOBILE_LOCK_TOUCH_PASS',elapsed_ms:lockElapsed,lockTouch,
      rendering_before:renderingBeforeLock,
      rendering:await page.evaluate(()=>window.__celineRoomEditorE2E.mobileRenderStats())});
    assert(lockTouch.touchstart>=1&&lockTouch.pointerdown>=1&&lockTouch.click>=1,
      'Mobile lock must receive genuine touch/pointer/click events');
    assert(lockElapsed<5000,'Genuine mobile lock remains too slow for interactive touch (<5s required)');
    assert.equal(await page.locator('#x').isDisabled(),true);
    await observeRealTap(page.locator('#mobile-lock'),"page.locator('#mobile-lock')");
    assert.equal(await page.locator('#x').isEnabled(),true);
    await observeRealTap(page.locator('#mobile-lock'),"page.locator('#mobile-lock')");
    record.push({name:'MOBILE_EDIT_UNDO_REDO_LOCK_PASS'});
    // A new 44px S25 shortcut is at the stage top-right. The former orbit
    // swipe used x=87%,y=20% and now falls on that button rather than WebGL.
    // Keep the original gesture as diagnostic, but START the true CDP touch
    // only on a dynamically verified, unobstructed renderer canvas pixel.
    const rect=await page.locator('#viewport canvas').boundingBox();
    assert(rect&&rect.width>0&&rect.height>0,'Real mobile WebGL canvas must have size');
    const formerPoint={x:Math.round(rect.x+rect.width*.87),
      y:Math.round(rect.y+rect.height*.20)};
    const orbitPoint={x:Math.round(rect.x+rect.width*.53),
      y:Math.round(rect.y+rect.height*.20)};
    const orbitHit=await page.evaluate(({formerPoint,orbitPoint})=>{
      const canvas=document.querySelector('#viewport canvas');
      const inspect=point=>{
        const hit=document.elementFromPoint(point.x,point.y);
        return {...point,hit_id:hit?.id||null,hit_tag:hit?.tagName||null,
          is_actual_canvas:hit===canvas};
      };
      const button=document.getElementById('mobile-s25-shortcut');
      const box=button?.getBoundingClientRect();
      return {former:inspect(formerPoint),verified:inspect(orbitPoint),
        s25_button_rect:box?[box.x,box.y,box.width,box.height]:null,
        s25_button_disabled:button?.disabled??null};
    },{formerPoint,orbitPoint});
    record.push({name:'REAL_MOBILE_ORBIT_GESTURE_HIT_PREFLIGHT',orbitHit});
    fs.writeFileSync(path.join(out,'mobile-orbit-hit-preflight.json'),
      JSON.stringify({source_sha:process.env.GITHUB_SHA||null,orbitHit},null,2));
    assert(orbitHit.verified.is_actual_canvas,
      'One-finger orbit must start on genuine WebGL canvas, not mobile shortcut/form/UI');
    const sx=orbitPoint.x,sy=orbitPoint.y;
    const cdp=await context.newCDPSession(page);
    const before=await page.evaluate(()=>window.__celineRoomEditorE2E.cameraProjection().camera_position_m);
    await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:sx,y:sy,id:1}]});
    for(let k=1;k<=6;k++)await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:sx-8*k,y:sy+5*k,id:1}]});
    await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
    await page.waitForTimeout(350);
    const after=await page.evaluate(()=>window.__celineRoomEditorE2E.cameraProjection().camera_position_m);
    assert(before.some((v,i)=>Math.abs(v-after[i])>0.002),'one-finger mobile orbit did not move camera');
    record.push({name:'GENUINE_CHROMIUM_SINGLE_FINGER_ORBIT_PASS',before,after});
    await observeRealTap(page.locator('[data-mobile-zoom="0.82"]'),"page.locator('[data-mobile-zoom=\"0.82\"]')");
    await observeRealTap(page.locator('[data-mobile-zoom="1.22"]'),"page.locator('[data-mobile-zoom=\"1.22\"]')");
    record.push({name:'MOBILE_ZOOM_BUTTONS_PASS'});
    const nativeAfter=await captureRealWebGL(page,'mobile-s25-real-13glb-webgl.png');
    record.push({name:'MOBILE_NATIVE_REAL_WEBGL_AFTER_GESTURES',nativeAfter});
    const downloadPromise=page.waitForEvent('download');
    await observeRealTap(page.locator('[data-mobile-action="save"]'),"page.locator('[data-mobile-action=\"save\"]')");
    const dl=await downloadPromise, saved=path.join(out,'mobile-export-all-13.json');
    await dl.saveAs(saved);
    const json=JSON.parse(fs.readFileSync(saved,'utf8'));
    assert.equal(json.schema,'yahya-room-layout-editor/v1');
    assert.deepEqual(json.room,{width:4.4,depth:4.2,height:2.65});
    assert.equal(json.objects.length,13);
    assert.equal(new Set(json.objects.map(o=>o.id)).size,13);
    assert.equal(json.objects.find(o=>o.id==='room_bed').locked,true);
    await completedMobileJsonImport(page,saved,'FIRST_EXPORTED_JSON_REIMPORT');
    const firstReload=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
    assert.equal(firstReload.model_count,13);
    for(const expected of json.objects){
      const actual=firstReload.objects.find(o=>o.id===expected.id);
      assert(actual?.loaded,'Exported JSON source GLB '+expected.id);
      assert.equal(actual.locked,expected.locked,'Exported JSON lock '+expected.id);
      for(const key of ['translation_m','rotation_euler_deg','scale_xyz'])
        for(let i=0;i<3;i++)
          assert(Math.abs(actual.transform[key][i]-expected[key][i])<1e-9,
            'Exported JSON import transform '+expected.id+' '+key+'['+i+']');
    }
    assert.equal(await page.locator('#mobile-object').inputValue(),'room_bed');
    assert.equal(await page.locator('#x').isDisabled(),true);
    record.push({name:'MOBILE_JSON_EXPORT_IMPORT_13_LOCKS_PASS'});
    assert.deepEqual(errors,[],'No browser/HTTP errors allowed');
    // Restore the OWNER'S exact approved JSON after the deliberate touch/nudge
    // exercises. The compositor image must show the user's unmodified baseline,
    // not the test's temporary +X bed move or temporary orbit/zoom.
    const approvedFile=path.join(__dirname,'layout-candidates',
      '2026-10-31-s25-user-approved-final-13glb.json');
    await completedMobileJsonImport(page,approvedFile,'SECOND_OWNER_FINAL_JSON_REIMPORT');
    const restored=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
    assert.equal(restored.model_count,13);
    assert.equal(restored.all_source_fidelity_glbs_loaded,true);
    for(const expected of approved.objects){
      const actual=restored.objects.find(o=>o.id===expected.id);
      assert(actual?.loaded,'Owner-final screenshot source GLB '+expected.id);
      assert.equal(actual.locked,expected.locked,'Owner-final screenshot lock '+expected.id);
      for(const key of ['translation_m','rotation_euler_deg','scale_xyz'])
        for(let i=0;i<3;i++)
          assert(Math.abs(actual.transform[key][i]-expected[key][i])<1e-9,
            'Owner-final screenshot transform '+expected.id+' '+key+'['+i+']');
    }

    // The owner's SAME exact input must reproduce identical actual source
    // geometry after the second JSON import; unexpected drift is a hard FAIL.
    for(const object of restored.objects){
      const base=ownerBounds[object.id],box=object.conservative_world_aabb_m;
      assert(base&&box,'Missing source bounds after owner reload '+object.id);
      for(let i=0;i<3;i++){
        assert(Number.isFinite(box.min[i])&&Math.abs(box.min[i]-base.min[i])<1e-6,
          'Unexpected owner raw GLB min-bound change '+object.id+' axis '+i);
        assert(Number.isFinite(box.max[i])&&Math.abs(box.max[i]-base.max[i])<1e-6,
          'Unexpected owner raw GLB max-bound change '+object.id+' axis '+i);
      }
      assert(Math.abs(object.floor_bottom_y_m-base.ground)<1e-6,
        'Unexpected owner floor-bound change '+object.id);
      for(const face of faces){
        const v=object.overflow_faces_m?.[face];
        assert(Number.isFinite(v)&&Math.abs(v-base.overflow[face])<1e-6,
          'Unexpected owner boundary overflow change '+object.id+' '+face);
      }
    }
    record.push({name:'OWNER_13_RAW_SOURCE_BOUNDS_UNCHANGED_AFTER_SECOND_IMPORT_PASS',
      field_tolerance_m:0.000001,diagnostic_file:'owner-final-source-boundary-diagnostic.json'});

    // Earlier full viewport succeeded at scrollOffsetY=1640: although a real
    // screenshot, it mostly showed the scrolled form rather than the room.
    // Restore the default editor camera and the initial 412x915 visual frame.
    // Touch/form scroll left the sticky stage at document y=0 while Chrome's
    // compositor later reported scrollOffsetY=1640. A synchronous scrollY=0
    // read alone does NOT prove a committed top-of-page viewport frame.
    // Unfocus lingering form controls, wait for two animation frames, then
    // check page, visual viewport and stage before real compositor capture.
    // Return the mobile editing controls to their compact default before
    // the genuine full viewport proof; do not crop, replace or hide the GLBs.
    await observeRealTap(page.locator('#mobile-tools-panel > summary'),"page.locator('#mobile-tools-panel > summary')");
    const mobileChrome=await page.evaluate(()=>{
      const panel=document.getElementById('mobile-tools-panel');
      const tools=document.getElementById('mobile-controls').getBoundingClientRect();
      const stage=document.getElementById('viewport').getBoundingClientRect();
      return {panel_open:panel.open,controls_height:tools.height,stage_height:stage.height,
        visible_stage_fraction:(stage.height-tools.height)/stage.height};
    });
    assert.equal(mobileChrome.panel_open,false,
      'Full phone view must not be obscured by expanded editor controls');
    assert(mobileChrome.visible_stage_fraction>0.70,
      'At least 70% of genuine mobile WebGL stage must remain unobscured by tools');
    record.push({name:'OWNER_FINAL_COMPACT_MOBILE_CONTROLS_PASS',mobileChrome});
    const captureScroll=await page.evaluate(async()=>{
      document.getElementById('camera-persp').click();
      if(document.activeElement instanceof HTMLElement)document.activeElement.blur();
      window.scrollTo({top:0,left:0,behavior:'instant'});
      await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));
      return {scroll_y:window.scrollY,
        visual_page_top:window.visualViewport?.pageTop??null,
        stage_top:document.getElementById('viewport').getBoundingClientRect().top,
        focused:document.activeElement?.id||document.activeElement?.tagName||null};
    });
    assert.equal(captureScroll.scroll_y,0,'Owner-final mobile capture must start at page top');
    assert.equal(Math.round(captureScroll.visual_page_top??-1),0,
      'Owner-final mobile visual viewport must start at page top');
    assert(captureScroll.stage_top>0,
      'Unscrolled mobile capture must show room below header, not a sticky-scrolled form');
    record.push({name:'OWNER_FINAL_13_GLB_RESTORED_FOR_TOP_VIEWPORT',
      layout_sha256:approvedSha,objects:13,locks:approved.objects.filter(o=>o.locked).length,
      capture_scroll_preflight:captureScroll,
      camera:'existing 3D default; stored owner transforms untouched'});
    // Compositor capture is last; if it stalls, preserve native PNG and JSON.
    // A missing true full-viewport shot is a FAIL, never a mobile visual PASS.
    let compositorError=null;
    try {
      // V47: V44/V45 Playwright screenshots and V46 direct CDP capture all
      // blocked while genuine 13-GLB canvas/real touchscreen/JSON passed.
      // Request a native Chromium compositor screencast frame as an
      // independent FULL viewport diagnostic, never stitch a replacement.
      // Keep the same strict 412x915 pixel and 10s acceptance limits.
      const shotSession=await context.newCDPSession(page);
      let shotTimeout, framesSeen=0;
      const staleFrames=[];
      const framePromise=new Promise(resolve=>{
        shotSession.on('Page.screencastFrame',frame=>{
          framesSeen++;
          void shotSession.send('Page.screencastFrameAck',
            {sessionId:frame.sessionId}).catch(()=>{});
          const offset=Math.round(frame.metadata?.scrollOffsetY??-1);
          if(offset===0)resolve(frame);
          else staleFrames.push({frame:framesSeen,scroll_offset_y:offset,
            timestamp:frame.metadata?.timestamp??null});
        });
      });
      const snapshotBefore=await page.evaluate(()=>
        window.__celineRoomEditorE2E.mobileRenderStats());
      const captureStarted=Date.now();
      const capture=await Promise.race([
        (async()=>{
          await shotSession.send('Page.enable');
          await shotSession.send('Page.startScreencast',{
            format:'png',maxWidth:412,maxHeight:915,everyNthFrame:1
          });
          // Same viewport and scene: requests a real WebGL redraw, does not
          // alter source GLBs, any stored transform, lock, or camera setting.
          await page.evaluate(()=>window.dispatchEvent(new Event('resize')));
          return await framePromise;
        })(),
        new Promise((_,reject)=>{
          shotTimeout=setTimeout(
            ()=>reject(new Error('Native Chromium viewport screencast exceeded 10000ms')),
            10000);
        })
      ]).finally(()=>{
        clearTimeout(shotTimeout);
        void shotSession.send('Page.stopScreencast').catch(()=>{});
      });
      const full=Buffer.from(capture.data,'base64');
      assert.equal(full.subarray(1,4).toString('ascii'),'PNG');
      assert(full.length>2000,'CDP full-viewport PNG unexpectedly small');
      fs.writeFileSync(path.join(out,'mobile-s25-viewport-412x915.png'),full);
      record.push({name:'MOBILE_CHROMIUM_SCREENCAST_VIEWPORT_DIAGNOSTIC',
        backend:'Page.startScreencast genuine Chromium compositor PNG',
        frames_seen:framesSeen,rejected_scrolled_frames:staleFrames,
        frame_metadata:capture.metadata,
        elapsed_ms:Date.now()-captureStarted,
        rendering_before:snapshotBefore,
        rendering_after:await page.evaluate(()=>
          window.__celineRoomEditorE2E.mobileRenderStats())});
      const dimensions=[full.readUInt32BE(16),full.readUInt32BE(20)];
      assert.deepEqual(dimensions,[412,915],
        'True full 412x915 CSS-pixel screenshot required; DPR3 emulated device stays intact');
      assert.equal(Math.round(capture.metadata?.scrollOffsetY??-1),0,
        'True mobile screenshot must show top-of-page room, not scrolled form controls');
      record.push({name:'MOBILE_FULL_VIEWPORT_COMPOSITOR_CAPTURE_PASS',
        filename:'mobile-s25-viewport-412x915.png',
        sha256:crypto.createHash('sha256').update(full).digest('hex'),pixels:dimensions});
    } catch(err) {
      compositorError=String(err.stack||err);
      // Isolation controls are diagnostic ONLY, NEVER mobile visual evidence:
      // compare a blank browser page and the same real editor UI with ONLY
      // canvas temporarily hidden, so compositor vs WebGL can be distinguished.
      // Each uses a shorter, not relaxed, 3-second screenshot timeout.
      try {
        const simplePage=await context.newPage();
        try {
          await simplePage.setContent('<!doctype html><html><body style="background:white">Chromium control</body></html>');
          const control=await simplePage.screenshot({timeout:3000,scale:'css'});
          record.push({name:'DIAGNOSTIC_ONLY_BLANK_PAGE_COMPOSITOR',
            result:'PASS',sha256:crypto.createHash('sha256').update(control).digest('hex'),
            pixels:[control.readUInt32BE(16),control.readUInt32BE(20)]});
        } finally {await simplePage.close();}
      } catch(controlError) {
        record.push({name:'DIAGNOSTIC_ONLY_BLANK_PAGE_COMPOSITOR',
          result:'FAIL',error:String(controlError)});
      }
      try {
        await page.evaluate(()=>{document.querySelector('#viewport canvas').style.visibility='hidden';});
        try {
          const hidden=await page.screenshot({timeout:3000,scale:'css'});
          record.push({name:'DIAGNOSTIC_ONLY_REAL_EDITOR_CANVAS_HIDDEN',
            result:'PASS_NOT_VISUAL_PROOF',
            sha256:crypto.createHash('sha256').update(hidden).digest('hex'),
            pixels:[hidden.readUInt32BE(16),hidden.readUInt32BE(20)]});
        } finally {
          await page.evaluate(()=>{document.querySelector('#viewport canvas').style.visibility='visible';});
        }
      } catch(hiddenError) {
        record.push({name:'DIAGNOSTIC_ONLY_REAL_EDITOR_CANVAS_HIDDEN',
          result:'FAIL',error:String(hiddenError)});
      }
      record.push({name:'MOBILE_FULL_VIEWPORT_COMPOSITOR_CAPTURE_FAIL',
        error:compositorError,native_before:nativeFirst.sha256,native_after:nativeAfter.sha256});
      fs.writeFileSync(path.join(out,'mobile-compositor-diagnostic.json'),
        JSON.stringify({source_sha:process.env.GITHUB_SHA||null,record,errors},null,2));
    }
    if(compositorError)throw new Error('Full 412x915 screenshot blocked despite native GLB/JSON evidence: '+compositorError);
    // Read-only raster diagnostic AFTER every strict touch, JSON and real viewport
    // gate. This cannot perturb the protected mobile <5s actions or owner view.
    // The standalone 99MB S25 source REQUESTS antialias:true, DPR cap 1.45.
    // We have not claimed its on-device getContextAttributes() as proven.
    // These detached WebGL2 contexts exercise ACTUAL AA availability on the
    // same mobile Chromium/SwiftShader GPU, but never render replacement GLBs.
    try {
      const msaa=await page.evaluate(()=>{
        const canvas=document.querySelector('#viewport canvas');
        const context=canvas.getContext('webgl2');
        if(!context)throw new Error('Live room has no readable WebGL2 context');
        const rect=canvas.getBoundingClientRect();
        const current={
          attributes:context.getContextAttributes(),
          framebuffer_samples:context.getParameter(context.SAMPLES),
          drawing_buffer_px:[context.drawingBufferWidth,context.drawingBufferHeight],
          canvas_css_px:[rect.width,rect.height],
          css_viewport_px:[innerWidth,innerHeight],
          window_device_pixel_ratio:devicePixelRatio
        };
        function triangleProbe(wantAA){
          const c=document.createElement('canvas');c.width=128;c.height=128;
          const g=c.getContext('webgl2',{
            alpha:false,antialias:wantAA,preserveDrawingBuffer:true,depth:false
          });
          if(!g)return {requested_antialias:wantAA,error:'WebGL2 unavailable'};
          const compile=(kind,src)=>{
            const sh=g.createShader(kind);g.shaderSource(sh,src);g.compileShader(sh);
            if(!g.getShaderParameter(sh,g.COMPILE_STATUS))
              throw new Error('AA probe shader: '+g.getShaderInfoLog(sh));
            return sh;
          };
          const v=compile(g.VERTEX_SHADER,
            '#version 300 es\nlayout(location=0) in vec2 p; void main(){gl_Position=vec4(p,0.,1.);}');
          const f=compile(g.FRAGMENT_SHADER,
            '#version 300 es\nprecision highp float;out vec4 o;void main(){o=vec4(1.);}');
          const prog=g.createProgram();g.attachShader(prog,v);g.attachShader(prog,f);
          g.linkProgram(prog);
          if(!g.getProgramParameter(prog,g.LINK_STATUS))
            throw new Error('AA probe link: '+g.getProgramInfoLog(prog));
          g.useProgram(prog);
          const vao=g.createVertexArray();g.bindVertexArray(vao);
          const buf=g.createBuffer();g.bindBuffer(g.ARRAY_BUFFER,buf);
          g.bufferData(g.ARRAY_BUFFER,new Float32Array([
            -0.85,-0.85, 0.80,-0.60, -0.65,0.85
          ]),g.STATIC_DRAW);
          g.enableVertexAttribArray(0);g.vertexAttribPointer(0,2,g.FLOAT,false,0,0);
          g.disable(g.DITHER);g.viewport(0,0,128,128);
          g.clearColor(0,0,0,1);g.clear(g.COLOR_BUFFER_BIT);
          g.drawArrays(g.TRIANGLES,0,3);
          const rgba=new Uint8Array(128*128*4);
          g.readPixels(0,0,128,128,g.RGBA,g.UNSIGNED_BYTE,rgba);
          let black=0,white=0,partial=0;
          for(let i=0;i<rgba.length;i+=4){
            if(rgba[i]===0)black++;
            else if(rgba[i]===255)white++;
            else partial++;
          }
          const result={
            requested_antialias:wantAA,actual_attributes:g.getContextAttributes(),
            framebuffer_samples:g.getParameter(g.SAMPLES),
            pixel_summary:{black,white,partial,total:128*128},
            gl_error:g.getError(),drawing_buffer_px:[g.drawingBufferWidth,g.drawingBufferHeight]
          };
          g.getExtension('WEBGL_lose_context')?.loseContext();
          return result;
        }
        return {
          current_room_canvas:current,
          aa_on_control:triangleProbe(true),
          aa_off_control:triangleProbe(false),
          original_s25_source_request:{webgl2:true,antialias:true,
            device_pixel_ratio_cap:1.45,actual_on_owner_device:'NOT_MEASURED'},
          interpretation_limit:'Controls prove the same Chromium GPU AA capability, not the original device or an exact furniture-frame AA pixel comparison'
        };
      });
      fs.writeFileSync(path.join(out,'mobile-s25-msaa-raster-readback.json'),
        JSON.stringify({source_sha:process.env.GITHUB_SHA||null,
          approved_layout_sha256:approvedSha,msaa,visual_acceptance:'NOT_GRANTED'},null,2));
      record.push({name:'S25_MSAA_ACTUAL_WEBGL_CONTEXT_DIAGNOSTIC',
        file:'mobile-s25-msaa-raster-readback.json',
        msaa,owner_json_unchanged:true,visual_acceptance:'NOT_GRANTED'});
    }catch(msaaError){
      // Instrumentation is non-acceptance evidence. Never relax any prior gate.
      record.push({name:'S25_MSAA_DIAGNOSTIC_UNAVAILABLE',
        error:String(msaaError.stack||msaaError),visual_acceptance:'NOT_GRANTED'});
    }


    // Non-acceptance diagnosis at the exact same browser scene, after the
    // strict real user-touch + Save/Reload + 412x915 compositor gates.
    let independentCdp;
    try {
      independentCdp=await independentNativeCdpInputReceipt(page,context);
      assert(independentCdp.owner_json_unchanged,
        'Independent stationary CDP input unexpectedly changed owner JSON');
    } catch(error) {
      independentCdp={diagnostic_error:String(error.stack||error),
        product_acceptance:'NOT_GRANTED'};
    }

    const browserTrace=await page.evaluate(()=>
      window.__celineMobileLatencyTrace||null).catch(error=>({
        trace_error:String(error)
      }));
    const timed=summarizeTouchAttribution(browserTrace,observedMobileTaps);
    const slow=timed.filter(tap=>tap.elapsed_ms>=5000);
    const latencyReport={
      source_sha:process.env.GITHUB_SHA||null,
      mobile_mode:NATIVE_AA4_MODE?'native_msaa4':'native_aa0',
      original_13_source_glbs:true,owner_json_sha256:approvedSha,
      source_layout_unchanged:true,
      strict_touch_redo_lock_orbit_import_screenshot_gates:'UNMODIFIED',
      timeout_ms_diagnostic_only:5000,
      playwright_actionability_debug_source:'Existing GitHub mobile job DEBUG=pw:api with exact ISO timestamps; distinct pre-visible/stable and performing-tap stages are in the unchanged job log, not inferred from DOM events',
      observed_real_touch_count:timed.length,
      long_tail_taps_ge_5000ms:slow.map(t=>({
        label:t.label,elapsed_ms:t.elapsed_ms,
        pointerdown_after_start_ms:t.pointerdown_after_start_ms,
        max_raf_gap_ms:t.max_raf_gap_ms,
        max_longtask_ms:t.max_longtask_ms
      })),
      native_independent_cdp:independentCdp,
      real_taps:timed,
      browser_main_thread_longtasks:browserTrace?.longtasks||[],
      browser_raf_gaps:browserTrace?.raf_gaps||[],
      browser_trace_error:browserTrace?.trace_error||null,
      product_aa4_promoted:false,
      visual_acceptance:'NOT_GRANTED'
    };
    fs.writeFileSync(path.join(out,'mobile-aa-touch-latency-attribution.json'),
      JSON.stringify(latencyReport,null,2));
    record.push({name:'NATIVE_AA_INPUT_LATENCY_PHASE_ATTRIBUTION_DIAGNOSTIC',
      mode:latencyReport.mobile_mode,real_taps:timed.length,
      slow_taps_ge_5000ms:slow.length,
      native_control_pointer_receipt:independentCdp.native_pointer_received??false,
      full_file:'mobile-aa-touch-latency-attribution.json',
      product_aa4_promoted:false,visual_acceptance:'NOT_GRANTED'});

    const finalNativeStats=await page.evaluate(()=>
      window.__celineRoomEditorE2E.mobileRenderStats());
    record.push({name:'NATIVE_MOBILE_AA_AFTER_ALL_STRICT_TOUCH_AND_JSON_GATES',
      mode:NATIVE_AA4_MODE?'native_4x_msaa':'native_aa0',
      final_render_stats:finalNativeStats,
      original_13_GLBS:true,viewport_px:[412,915],
      original_owner_json_sha256:approvedSha});
    // P1: do not allow a green result when either native control was unmeasured
    // or exceeded the unchanged 5000ms threshold. Kept AFTER all pre-existing
    // functional/JSON/Orbit/412x915 gates so genuine evidence survives RED.
    assert.equal(targetComparison?.strict_5s_all_taps,true,
      'Real paused WebGL-canvas versus HTML CDP touch differential was not <5000ms for all four taps');
    assert.equal(targetComparison?.paused_render_zero_all,true,
      'Differential controls must operate with zero WebGL frames');
    const result={verdict:'PASS_TECHNICAL_VISUAL_REVIEW_REQUIRED',source_sha:process.env.GITHUB_SHA||null,device:'Samsung S25 Ultra-like Android Chrome emulation (not physical Samsung)',record,errors};
    fs.writeFileSync(path.join(out,'mobile-proof.json'),JSON.stringify(result,null,2));
    console.log('CELINE_ROOM_EDITOR_MOBILE_TOUCH_13_SOURCE_GLB_JSON_PASS');
  } finally {
    // Always preserve browser-target touch attribution BEFORE closing Chromium,
    // even when a strict AA4 latency assertion exits before the final report.
    // This is diagnostic evidence only: it never changes an assertion/verdict.
    try {
      const trace=mobilePage?
        await mobilePage.evaluate(() => window.__celineMobileLatencyTrace||null)
          .catch(error=>({trace_error:String(error)})):null;
      const timed=summarizeTouchAttribution(trace,observedMobileTaps);
      const unmatched=timed.filter(t=>t.target_attribution!=='TRUSTED_EXPECTED_POINTER')
        .map(t=>({label:t.label,elapsed_ms:t.elapsed_ms,
          target_attribution:t.target_attribution,
          expected_event_target:t.expected_event_target,
          ignored_nearby_unmatched_events:t.ignored_nearby_unmatched_events}));
      const partial={
        source_sha:process.env.GITHUB_SHA||null,
        mode:NATIVE_AA4_MODE?'native_aa4':'native_aa0',
        capture_reason:'ALWAYS_SAVE_PARTIAL_EVEN_ON_EARLY_FAIL',
        verdict:'DIAGNOSTIC_ONLY_NOT_A_TEST_PASS',
        strict_lt_5000_ms_gate:'UNCHANGED',
        observed_real_touch_count:timed.length,
        long_tail_taps_ge_5000ms:timed.filter(t=>t.elapsed_ms>=5000)
          .map(t=>({label:t.label,elapsed_ms:t.elapsed_ms,
            pointerdown_after_start_ms:t.pointerdown_after_start_ms,
            target_attribution:t.target_attribution})),
        unmatched_targets:unmatched,
        // The independently recorded actionability/rect/hit tests are kept
        // as their original files, even if main exits before the full report.
        existing_actionability_files:[
          'mobile-neutral-original-actionability.json',
          'mobile-neutral-restore-actionability.json',
          'mobile-s25-enter-actionability.json'
        ].filter(name=>fs.existsSync(path.join(out,name))),
        playwright_api_debug_log:'workflow mobile job DEBUG=pw:api',
        browser_trace_error:trace?.trace_error||null,
        real_taps:timed,
        visual_acceptance:'NOT_GRANTED'
      };
      fs.writeFileSync(path.join(out,'mobile-aa-touch-latency-partial.json'),
        JSON.stringify(partial,null,2));
    } catch(error) {
      fs.writeFileSync(path.join(out,'mobile-aa-touch-latency-partial-error.json'),
        JSON.stringify({diagnostic_error:String(error.stack||error),
          product_acceptance:'NOT_GRANTED'},null,2));
    }
    await browser.close();
  }
}
main().catch(err=>{
  console.error(err.stack||String(err));
  fs.writeFileSync(path.join(out,'mobile-proof.json'),JSON.stringify({verdict:'FAIL',record,errors,observedMobileTaps,error:String(err.stack||err)},null,2));
  process.exitCode=1;
});
