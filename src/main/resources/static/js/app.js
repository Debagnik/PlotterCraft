/**
 * PlotterCraft - Main Frontend Controller & Three.js Slicer Visualizer
 */

// Global State
const state = {
    currentJob: null,
    moves: [],
    svgFiles: {},
    playback: {
        isPlaying: false,
        speed: 1,
        speeds: [1, 2, 5, 10, 25],
        currentIndex: 0,
        progress: 1.0, // 0.0 to 1.0
        lastFrameTime: null
    },
    viewOptions: {
        showTravel: true,
        showToolhead: true
    }
};

// Three.js References
let scene, camera, renderer, controls;
let bedGroup, paperMesh, toolheadGroup;
let drawLineSegments, travelLineSegments;
let drawPositions = [], travelPositions = [];
let totalDrawVertices = 0, totalTravelVertices = 0;

// Initialize on DOM Loaded
document.addEventListener('DOMContentLoaded', () => {
    lucide.createIcons();
    initThreeScene();
    initUIEventListeners();
    loadDefaultSample();
    window.addEventListener('resize', onWindowResize);
});

// =============================================================================
// THREE.JS SCENE INITIALIZATION & BED CONSTRUCTION
// =============================================================================

function initThreeScene() {
    const container = document.getElementById('canvasContainer');
    const width = container.clientWidth;
    const height = container.clientHeight;

    // 1. Scene & Background
    scene = new THREE.Scene();
    scene.background = new THREE.Color(0x0f1115);

    // 2. Camera Setup (Centered on Bambu A1 256x256 mm Bed)
    camera = new THREE.PerspectiveCamera(45, width / height, 1, 2000);
    setCameraIsometric();

    // 3. WebGL Renderer
    const canvas = document.getElementById('threeCanvas');
    renderer = new THREE.WebGLRenderer({ canvas: canvas, antialias: true, alpha: false });
    renderer.setSize(width, height);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.shadowMap.enabled = true;

    // 4. OrbitControls
    controls = new THREE.OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;
    controls.dampingFactor = 0.05;
    controls.target.set(128, 128, 0); // Center of Bambu A1 bed
    controls.maxPolarAngle = Math.PI / 2 - 0.02; // Don't go below bed plane

    // 5. Lighting
    const ambientLight = new THREE.AmbientLight(0xffffff, 0.7);
    scene.add(ambientLight);

    const dirLight = new THREE.DirectionalLight(0xffffff, 0.8);
    dirLight.position.set(128, 300, 250);
    dirLight.target.position.set(128, 128, 0);
    scene.add(dirLight);
    scene.add(dirLight.target);

    // 6. Build Plate & Paper Construction
    createBambuA1Bed();
    createToolheadStylus();

    // 7. Start Render Loop
    animate();
}

function createBambuA1Bed() {
    bedGroup = new THREE.Group();

    // Bambu Lab A1 Bed Dimensions: 256mm x 256mm
    // Base Plate (Dark textured steel plate)
    const plateGeo = new THREE.BoxGeometry(256, 256, 3);
    const plateMat = new THREE.MeshStandardMaterial({
        color: 0x1a1e27,
        roughness: 0.8,
        metalness: 0.2
    });
    const plateMesh = new THREE.Mesh(plateGeo, plateMat);
    plateMesh.position.set(128, 128, -1.5);
    bedGroup.add(plateMesh);

    // Bed Grid Lines (10mm minor grid, 50mm major grid)
    const gridHelper = new THREE.GridHelper(256, 25, 0x00ae42, 0x2a3142);
    gridHelper.rotation.x = Math.PI / 2;
    gridHelper.position.set(128, 128, 0.05);
    bedGroup.add(gridHelper);

    // Bambu Origin Marker at (0, 0, 0)
    const axesHelper = new THREE.AxesHelper(25);
    axesHelper.position.set(0, 0, 0.2);
    bedGroup.add(axesHelper);

    // Build Volume Wireframe Box (256 x 256 x 256 mm)
    const volumeGeo = new THREE.BoxGeometry(256, 256, 256);
    const volumeEdges = new THREE.EdgesGeometry(volumeGeo);
    const volumeLine = new THREE.LineSegments(volumeEdges, new THREE.LineBasicMaterial({
        color: 0x232936,
        transparent: true,
        opacity: 0.4
    }));
    volumeLine.position.set(128, 128, 128);
    bedGroup.add(volumeLine);

    // Paper Sheet Mesh: Standard A4 width (210mm) centered on 256mm bed
    // Usable page length = 256 mm (remainder of 297mm A4 goes blank beyond bed)
    // Horizontal centering: (256 - 210) / 2 = 23 mm margin on left and right
    const a4Width = 210.0;
    const a4LengthOnBed = 256.0;
    const paperGeo = new THREE.PlaneGeometry(a4Width, a4LengthOnBed);
    const paperMat = new THREE.MeshStandardMaterial({
        color: 0xffffff,
        roughness: 0.95,
        metalness: 0.02
    });
    paperMesh = new THREE.Mesh(paperGeo, paperMat);
    paperMesh.position.set(128, 128, 0.1);
    bedGroup.add(paperMesh);

    // A4 Paper Border Line (crisp boundary showing 210mm width)
    const paperBorderGeo = new THREE.EdgesGeometry(paperGeo);
    const paperBorderLine = new THREE.LineSegments(paperBorderGeo, new THREE.LineBasicMaterial({
        color: 0x94a3b8,
        linewidth: 1
    }));
    paperBorderLine.position.set(128, 128, 0.15);
    bedGroup.add(paperBorderLine);

    scene.add(bedGroup);
}

function createToolheadStylus() {
    toolheadGroup = new THREE.Group();

    // Stylus Body (Brass/Silver metallic cone)
    const coneGeo = new THREE.ConeGeometry(3, 14, 16);
    const coneMat = new THREE.MeshStandardMaterial({
        color: 0xd97706, // Brass gold
        metalness: 0.85,
        roughness: 0.2
    });
    const coneMesh = new THREE.Mesh(coneGeo, coneMat);
    coneMesh.rotation.x = -Math.PI / 2;
    coneMesh.position.z = 7;
    toolheadGroup.add(coneMesh);

    // Pen Nib / Tip
    const tipGeo = new THREE.SphereGeometry(0.8, 12, 12);
    const tipMat = new THREE.MeshStandardMaterial({
        color: 0x1e3a8a, // Ink blue tip
        roughness: 0.3
    });
    const tipMesh = new THREE.Mesh(tipGeo, tipMat);
    tipMesh.position.z = 0.4;
    toolheadGroup.add(tipMesh);

    toolheadGroup.position.set(0, 0, 10);
    toolheadGroup.visible = false;
    scene.add(toolheadGroup);
}

// =============================================================================
// TOOLPATH GEOMETRY RENDERING (DUAL-COLOR BUFFER SYSTEM)
// =============================================================================

function renderToolpaths(moves) {
    // Remove existing toolpath lines from scene
    if (drawLineSegments) scene.remove(drawLineSegments);
    if (travelLineSegments) scene.remove(travelLineSegments);

    if (!moves || moves.length === 0) {
        toolheadGroup.visible = false;
        document.getElementById('visualizerEmptyState').classList.remove('hidden');
        return;
    }

    document.getElementById('visualizerEmptyState').classList.add('hidden');
    state.moves = moves;

    drawPositions = [];
    travelPositions = [];

    // Separate moves into DRAW (G1) and TRAVEL (G0)
    for (let i = 0; i < moves.length; i++) {
        const m = moves[i];
        const from = m.from;
        const to = m.to;

        // In 3D space: X=from.x, Y=(256 - from.y) to convert screen/SVG top-left Y to 3D Cartesian Y
        const p1x = from.x;
        const p1y = 256.0 - from.y;
        const p1z = from.z + 0.2; // Slight offset above paper surface

        const p2x = to.x;
        const p2y = 256.0 - to.y;
        const p2z = to.z + 0.2;

        if (m.type === 'DRAW') {
            drawPositions.push(p1x, p1y, p1z, p2x, p2y, p2z);
        } else {
            travelPositions.push(p1x, p1y, p1z, p2x, p2y, p2z);
        }
    }

    totalDrawVertices = drawPositions.length / 3;
    totalTravelVertices = travelPositions.length / 3;

    // 1. Draw Lines (Solid deep ink blue)
    const drawGeo = new THREE.BufferGeometry();
    drawGeo.setAttribute('position', new THREE.Float32BufferAttribute(drawPositions, 3));
    const drawMat = new THREE.LineBasicMaterial({
        color: 0x1d4ed8, // Vibrant ink royal blue
        linewidth: 2
    });
    drawLineSegments = new THREE.LineSegments(drawGeo, drawMat);
    scene.add(drawLineSegments);

    // 2. Travel Lines (Translucent light red aerial paths)
    const travelGeo = new THREE.BufferGeometry();
    travelGeo.setAttribute('position', new THREE.Float32BufferAttribute(travelPositions, 3));
    const travelMat = new THREE.LineBasicMaterial({
        color: 0xf87171,
        transparent: true,
        opacity: 0.45,
        linewidth: 1
    });
    travelLineSegments = new THREE.LineSegments(travelGeo, travelMat);
    travelLineSegments.visible = state.viewOptions.showTravel;
    scene.add(travelLineSegments);

    // Enable slider and play controls
    const slider = document.getElementById('sliderToolpath');
    slider.disabled = false;
    slider.value = 1000;
    document.getElementById('sliderPercentLabel').textContent = '100.0%';
    document.getElementById('btnPlayPause').disabled = false;
    document.getElementById('btnDownloadGcode').disabled = false;

    // Set initial toolhead position at end
    const lastMove = moves[moves.length - 1];
    updateToolheadPosition(lastMove.to.x, 256.0 - lastMove.to.y, lastMove.to.z + 0.2);
    toolheadGroup.visible = state.viewOptions.showToolhead;

    setScrubberProgress(1.0);
}

function setScrubberProgress(progress) {
    state.playback.progress = Math.max(0, Math.min(1.0, progress));
    const totalMoves = state.moves.length;
    if (totalMoves === 0) return;

    const visibleMoveCount = Math.round(state.playback.progress * totalMoves);
    state.playback.currentIndex = Math.max(0, visibleMoveCount - 1);

    // Calculate vertex count cutoffs
    let currentDrawVerts = 0;
    let currentTravelVerts = 0;

    for (let i = 0; i < visibleMoveCount; i++) {
        if (state.moves[i].type === 'DRAW') {
            currentDrawVerts += 2;
        } else {
            currentTravelVerts += 2;
        }
    }

    if (drawLineSegments) {
        drawLineSegments.geometry.setDrawRange(0, currentDrawVerts);
    }
    if (travelLineSegments) {
        travelLineSegments.geometry.setDrawRange(0, currentTravelVerts);
    }

    // Position toolhead at current move's 'to' point
    if (visibleMoveCount > 0 && visibleMoveCount <= totalMoves) {
        const activeMove = state.moves[visibleMoveCount - 1];
        updateToolheadPosition(activeMove.to.x, 256.0 - activeMove.to.y, activeMove.to.z + 0.2);
    } else if (visibleMoveCount === 0 && totalMoves > 0) {
        const firstMove = state.moves[0];
        updateToolheadPosition(firstMove.from.x, 256.0 - firstMove.from.y, firstMove.from.z + 0.2);
    }

    // Update UI Slider
    const slider = document.getElementById('sliderToolpath');
    slider.value = Math.round(state.playback.progress * 1000);
    document.getElementById('sliderPercentLabel').textContent = (state.playback.progress * 100).toFixed(1) + '%';
}

function updateToolheadPosition(x, y, z) {
    if (toolheadGroup) {
        toolheadGroup.position.set(x, y, z);
    }
}

// =============================================================================
// SLICER SIMULATION & ANIMATION LOOP
// =============================================================================

function animate(time) {
    requestAnimationFrame(animate);

    if (state.playback.isPlaying && state.moves.length > 0) {
        if (!state.playback.lastFrameTime) {
            state.playback.lastFrameTime = time;
        }
        const deltaSeconds = (time - state.playback.lastFrameTime) / 1000.0;
        state.playback.lastFrameTime = time;

        const totalMoves = state.moves.length;
        const currentMove = state.moves[state.playback.currentIndex] || state.moves[0];
        const moveDuration = Math.max(0.005, currentMove.durationSeconds);

        // Advance progress based on real kinematic move duration & speed multiplier
        const stepProgress = (deltaSeconds * state.playback.speed) / (moveDuration * totalMoves * 0.15);
        let newProgress = state.playback.progress + Math.max(0.0005, stepProgress);

        if (newProgress >= 1.0) {
            newProgress = 1.0;
            pauseSimulation();
        }

        setScrubberProgress(newProgress);
    } else {
        state.playback.lastFrameTime = null;
    }

    controls.update();
    renderer.render(scene, camera);
}

function togglePlayPause() {
    if (state.playback.isPlaying) {
        pauseSimulation();
    } else {
        playSimulation();
    }
}

function playSimulation() {
    if (state.playback.progress >= 1.0) {
        setScrubberProgress(0.0);
    }
    state.playback.isPlaying = true;
    document.getElementById('iconPlayPause').setAttribute('data-lucide', 'pause');
    lucide.createIcons();
}

function pauseSimulation() {
    state.playback.isPlaying = false;
    document.getElementById('iconPlayPause').setAttribute('data-lucide', 'play');
    lucide.createIcons();
}

// =============================================================================
// CAMERA PRESETS
// =============================================================================

function setCameraIsometric() {
    camera.position.set(128, -120, 260);
    camera.up.set(0, 0, 1);
    if (controls) {
        controls.target.set(128, 128, 0);
        controls.update();
    }
}

function setCameraTop() {
    camera.position.set(128, 128, 360);
    camera.up.set(0, 1, 0);
    if (controls) {
        controls.target.set(128, 128, 0);
        controls.update();
    }
}

function setCameraFront() {
    camera.position.set(128, -160, 30);
    camera.up.set(0, 0, 1);
    if (controls) {
        controls.target.set(128, 128, 0);
        controls.update();
    }
}

function onWindowResize() {
    const container = document.getElementById('canvasContainer');
    const width = container.clientWidth;
    const height = container.clientHeight;
    camera.aspect = width / height;
    camera.updateProjectionMatrix();
    renderer.setSize(width, height);
}

// =============================================================================
// API & USER INTERACTION HANDLERS
// =============================================================================

async function handleGenerateGcode() {
    const btn = document.getElementById('btnGenerateGcode');
    const markdown = document.getElementById('markdownInput').value;

    if (!markdown || markdown.trim().isEmpty) {
        alert('Please enter or load a Markdown assignment first.');
        return;
    }

    btn.disabled = true;
    btn.innerHTML = `<svg class="animate-spin -ml-1 mr-2 h-4 w-4 text-white inline" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path></svg> Generating...`;

    const payload = {
        markdown: markdown,
        svgFiles: state.svgFiles,
        feedrateDraw: parseFloat(document.getElementById('inputDrawFeedrate').value),
        feedrateTravel: parseFloat(document.getElementById('inputTravelFeedrate').value),
        zUp: parseFloat(document.getElementById('inputZUp').value),
        zDown: parseFloat(document.getElementById('inputZDown').value),
        jitterEnabled: document.getElementById('checkJitter').checked,
        jitterIntensity: parseFloat(document.getElementById('inputJitterIntensity').value),
        fontScale: parseFloat(document.getElementById('inputFontScale') ? document.getElementById('inputFontScale').value : 1.0),
        pageWidth: 256.0,
        pageHeight: 256.0,
        marginLeft: parseFloat(document.getElementById('selectMargins').value),
        marginRight: parseFloat(document.getElementById('selectMargins').value),
        marginTop: parseFloat(document.getElementById('selectMargins').value),
        marginBottom: parseFloat(document.getElementById('selectMargins').value)
    };

    try {
        const response = await fetch('/api/generate-gcode', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        const data = await response.json();
        if (data.success) {
            state.currentJob = data;
            renderToolpaths(data.moves);
            updateStatisticsDisplay(data.statistics);
            document.getElementById('modalGcodeText').value = data.gcode;
            document.getElementById('modalLineCount').textContent = `(${data.statistics.gcodeLinesCount} lines)`;
        } else {
            alert('Generation error: ' + data.message);
        }
    } catch (err) {
        console.error('API Error:', err);
        alert('Failed to connect to backend: ' + err.message);
    } finally {
        btn.disabled = false;
        btn.innerHTML = `<i data-lucide="play" class="w-4 h-4 fill-current inline mr-1"></i> Generate Toolpath`;
        lucide.createIcons();
    }
}

function updateStatisticsDisplay(stats) {
    if (!stats) return;
    document.getElementById('statEstTime').textContent = stats.formattedTime || '0s';
    document.getElementById('statDrawDist').textContent = stats.totalDrawDistanceMm + ' mm';
    document.getElementById('statTravelDist').textContent = stats.totalTravelDistanceMm + ' mm';
    document.getElementById('statPenLifts').textContent = stats.penLiftsCount;
    document.getElementById('statMoveCount').textContent = stats.totalMovesCount;
    document.getElementById('statStrokeCount').textContent = stats.strokeCount;
}

function handleDownloadGcode() {
    if (!state.currentJob || !state.currentJob.gcode) {
        alert('Please generate G-code first.');
        return;
    }

    const blob = new Blob([state.currentJob.gcode], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'plottercraft_assignment.gcode';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}

// =============================================================================
// SAMPLE ASSIGNMENTS & ASSETS MANAGEMENT
// =============================================================================

async function loadSample(sampleKey) {
    try {
        const res = await fetch('/api/samples');
        const samples = await res.json();
        const sample = samples[sampleKey];
        if (sample) {
            document.getElementById('markdownInput').value = sample.markdown;
            state.svgFiles = sample.svgFiles || {};
            renderSvgList();
            updateCharCount();
            handleGenerateGcode();
        }
    } catch (e) {
        console.error('Failed to load sample:', e);
    }
}

function loadDefaultSample() {
    loadSample('physics');
}

function renderSvgList() {
    const container = document.getElementById('svgListContainer');
    container.innerHTML = '';
    const keys = Object.keys(state.svgFiles);
    document.getElementById('svgBadgeCount').textContent = keys.length;

    if (keys.length === 0) {
        container.innerHTML = `<div class="text-xs text-slate-500 py-4 text-center">No SVG diagrams attached. Upload one or load a sample.</div>`;
        return;
    }

    keys.forEach(key => {
        const svgXml = state.svgFiles[key];
        const item = document.createElement('div');
        item.className = 'bg-studio-card border border-studio-border rounded-lg p-3 flex items-center justify-between';
        item.innerHTML = `
            <div class="flex items-center space-x-3">
                <div class="w-12 h-12 bg-white/5 rounded border border-studio-border flex items-center justify-center p-1 overflow-hidden">
                    <div class="w-full h-full text-slate-300 [&>svg]:w-full [&>svg]:h-full [&>svg]:stroke-current [&>svg]:fill-none">${svgXml}</div>
                </div>
                <div>
                    <div class="text-xs font-semibold text-slate-200">${key}</div>
                    <div class="text-[10px] text-slate-400 font-mono">Use in markdown: ![](${key})</div>
                </div>
            </div>
            <button class="btn-delete-svg p-1.5 hover:bg-studio-border text-red-400 hover:text-red-300 rounded transition" data-key="${key}" title="Remove SVG">
                <i data-lucide="trash-2" class="w-4 h-4"></i>
            </button>
        `;
        container.appendChild(item);
    });

    container.querySelectorAll('.btn-delete-svg').forEach(btn => {
        btn.addEventListener('click', (e) => {
            const k = btn.getAttribute('data-key');
            delete state.svgFiles[k];
            renderSvgList();
        });
    });

    lucide.createIcons();
}

function handleSvgUpload(e) {
    const files = e.target.files;
    if (!files) return;

    for (let file of files) {
        const reader = new FileReader();
        reader.onload = (event) => {
            state.svgFiles[file.name] = event.target.result;
            renderSvgList();
        };
        reader.readAsText(file);
    }
}

function updateCharCount() {
    const val = document.getElementById('markdownInput').value;
    document.getElementById('charCountLabel').textContent = val.length + ' chars';
}

// =============================================================================
// UI EVENT LISTENERS
// =============================================================================

function initUIEventListeners() {
    // Top Bar Buttons
    document.getElementById('btnGenerateGcode').addEventListener('click', handleGenerateGcode);
    document.getElementById('btnDownloadGcode').addEventListener('click', handleDownloadGcode);
    document.getElementById('btnLoadPhysics').addEventListener('click', () => loadSample('physics'));
    document.getElementById('btnLoadCalculus').addEventListener('click', () => loadSample('calculus'));

    // Tabs
    const tabBtnEditor = document.getElementById('tabBtnEditor');
    const tabBtnSvgs = document.getElementById('tabBtnSvgs');
    const tabBtnSettings = document.getElementById('tabBtnSettings');
    const tabContentEditor = document.getElementById('tabContentEditor');
    const tabContentSvgs = document.getElementById('tabContentSvgs');
    const tabContentSettings = document.getElementById('tabContentSettings');

    function switchTab(activeBtn, activeContent) {
        [tabBtnEditor, tabBtnSvgs, tabBtnSettings].forEach(b => b.classList.remove('active', 'text-white'));
        [tabContentEditor, tabContentSvgs, tabContentSettings].forEach(c => c.classList.add('hidden'));
        activeBtn.classList.add('active', 'text-white');
        activeContent.classList.remove('hidden');
    }

    tabBtnEditor.addEventListener('click', () => switchTab(tabBtnEditor, tabContentEditor));
    tabBtnSvgs.addEventListener('click', () => switchTab(tabBtnSvgs, tabContentSvgs));
    tabBtnSettings.addEventListener('click', () => switchTab(tabBtnSettings, tabContentSettings));

    // Markdown textarea change
    document.getElementById('markdownInput').addEventListener('input', updateCharCount);

    // SVG upload
    document.getElementById('svgFileInput').addEventListener('change', handleSvgUpload);

    // Range Sliders display update
    document.getElementById('inputDrawFeedrate').addEventListener('input', (e) => {
        document.getElementById('valDrawFeedrate').textContent = e.target.value + ' mm/min';
    });
    document.getElementById('inputTravelFeedrate').addEventListener('input', (e) => {
        document.getElementById('valTravelFeedrate').textContent = e.target.value + ' mm/min';
    });
    document.getElementById('inputZUp').addEventListener('input', (e) => {
        document.getElementById('valZUp').textContent = parseFloat(e.target.value).toFixed(1) + ' mm';
    });
    document.getElementById('inputZDown').addEventListener('input', (e) => {
        document.getElementById('valZDown').textContent = parseFloat(e.target.value).toFixed(1) + ' mm';
    });
    document.getElementById('inputJitterIntensity').addEventListener('input', (e) => {
        document.getElementById('valJitterIntensity').textContent = '±' + parseFloat(e.target.value).toFixed(2) + ' mm';
    });
    document.getElementById('checkJitter').addEventListener('change', (e) => {
        document.getElementById('jitterStatusTag').textContent = e.target.checked ? 'ON (±0.15mm)' : 'OFF';
        document.getElementById('jitterStatusTag').className = e.target.checked ? 'text-emerald-400 font-semibold' : 'text-slate-500 font-semibold';
    });

    // =============================================================================
    // PLOT SCALE CONTROLS (Dedicated Panel: Slider, Number Input, Steppers, Presets)
    // =============================================================================
    let scaleDebounceTimer = null;

    function setPlotScale(val, triggerGenerate = true, source = 'other') {
        val = Math.round(Math.max(0.5, Math.min(1.8, val)) * 100) / 100;
        
        const fontScaleInput = document.getElementById('inputFontScale');
        const numInput = document.getElementById('inputFontScaleNumber');
        const badge = document.getElementById('valFontScaleBadge');
        const badge3d = document.getElementById('badge3dScale');

        if (fontScaleInput && source !== 'slider') {
            fontScaleInput.value = val;
        }
        if (numInput && source !== 'number') {
            numInput.value = val.toFixed(2);
        }
        if (badge) {
            badge.textContent = val.toFixed(2) + 'x';
        }
        if (badge3d) {
            badge3d.textContent = val.toFixed(2) + 'x';
        }

        // Dynamically update CSS gradient track fill on the range input
        if (fontScaleInput) {
            const pct = Math.max(0, Math.min(100, ((val - 0.5) / (1.8 - 0.5)) * 100));
            fontScaleInput.style.setProperty('--scale-percent', pct.toFixed(1) + '%');
        }

        // Update active styling on 1-click preset buttons
        document.querySelectorAll('.btn-scale-preset').forEach(btn => {
            const btnScale = parseFloat(btn.getAttribute('data-scale'));
            if (Math.abs(btnScale - val) < 0.02) {
                btn.className = 'btn-scale-preset active px-2 py-1 bg-bambu-500/20 border border-bambu-500/40 rounded-md text-[11px] font-mono text-bambu-400 font-semibold transition';
            } else {
                btn.className = 'btn-scale-preset px-2 py-1 bg-studio-panel hover:bg-studio-hover border border-studio-border rounded-md text-[11px] font-mono text-slate-300 hover:text-white transition';
            }
        });

        if (triggerGenerate) {
            if (source === 'slider') {
                if (scaleDebounceTimer) clearTimeout(scaleDebounceTimer);
                scaleDebounceTimer = setTimeout(() => {
                    handleGenerateGcode();
                }, 220);
            } else {
                if (scaleDebounceTimer) clearTimeout(scaleDebounceTimer);
                handleGenerateGcode();
            }
        }
    }

    // 1. Slider Events
    const fontScaleInput = document.getElementById('inputFontScale');
    if (fontScaleInput) {
        fontScaleInput.addEventListener('input', (e) => {
            setPlotScale(parseFloat(e.target.value), true, 'slider');
        });
        fontScaleInput.addEventListener('change', (e) => {
            setPlotScale(parseFloat(e.target.value), true, 'slider');
        });
    }

    // 2. Direct Number Input Events
    const numInput = document.getElementById('inputFontScaleNumber');
    if (numInput) {
        numInput.addEventListener('change', (e) => {
            const parsed = parseFloat(e.target.value);
            setPlotScale(isNaN(parsed) ? 1.0 : parsed, true, 'number');
        });
        numInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                e.target.blur();
            }
        });
    }

    // 3. Stepper Buttons with Click & Hold-to-Repeat
    function setupHoldRepeat(button, stepDelta) {
        if (!button) return;
        let repeatTimer = null;
        let stepInterval = null;

        const doStep = () => {
            const curr = parseFloat(document.getElementById('inputFontScale')?.value || 1.0);
            setPlotScale(curr + stepDelta, true, 'stepper');
        };

        const startHold = (e) => {
            e.preventDefault();
            doStep();
            repeatTimer = setTimeout(() => {
                stepInterval = setInterval(doStep, 90);
            }, 300);
        };

        const stopHold = () => {
            if (repeatTimer) clearTimeout(repeatTimer);
            if (stepInterval) clearInterval(stepInterval);
            repeatTimer = null;
            stepInterval = null;
        };

        button.addEventListener('mousedown', startHold);
        button.addEventListener('mouseup', stopHold);
        button.addEventListener('mouseleave', stopHold);
        button.addEventListener('touchstart', startHold, { passive: false });
        button.addEventListener('touchend', stopHold);
    }

    setupHoldRepeat(document.getElementById('btnScaleDown'), -0.05);
    setupHoldRepeat(document.getElementById('btnScaleUp'), +0.05);

    // 4. Preset Buttons
    document.querySelectorAll('.btn-scale-preset').forEach(btn => {
        btn.addEventListener('click', (e) => {
            const scale = parseFloat(btn.getAttribute('data-scale'));
            setPlotScale(scale, true, 'preset');
        });
    });

    // Initialize initial visual fill
    setPlotScale(1.0, false);

    // 3D Slicer Scrubbing Slider
    const slider = document.getElementById('sliderToolpath');
    slider.addEventListener('input', (e) => {
        pauseSimulation();
        setScrubberProgress(parseFloat(e.target.value) / 1000.0);
    });

    // Simulation Controls
    document.getElementById('btnPlayPause').addEventListener('click', togglePlayPause);

    document.getElementById('btnSpeedMultiplier').addEventListener('click', () => {
        const currIdx = state.playback.speeds.indexOf(state.playback.speed);
        const nextIdx = (currIdx + 1) % state.playback.speeds.length;
        state.playback.speed = state.playback.speeds[nextIdx];
        document.getElementById('btnSpeedMultiplier').textContent = state.playback.speed + 'x';
    });

    document.getElementById('btnStepBack').addEventListener('click', () => {
        pauseSimulation();
        const step = 10 / Math.max(1, state.moves.length);
        setScrubberProgress(state.playback.progress - step);
    });

    document.getElementById('btnStepForward').addEventListener('click', () => {
        pauseSimulation();
        const step = 10 / Math.max(1, state.moves.length);
        setScrubberProgress(state.playback.progress + step);
    });

    // Camera preset buttons
    document.getElementById('btnViewIso').addEventListener('click', setCameraIsometric);
    document.getElementById('btnViewTop').addEventListener('click', setCameraTop);
    document.getElementById('btnViewFront').addEventListener('click', setCameraFront);
    document.getElementById('btnResetCamera').addEventListener('click', setCameraIsometric);

    // View toggles
    document.getElementById('checkShowTravel').addEventListener('change', (e) => {
        state.viewOptions.showTravel = e.target.checked;
        if (travelLineSegments) travelLineSegments.visible = e.target.checked;
    });
    document.getElementById('checkShowToolhead').addEventListener('change', (e) => {
        state.viewOptions.showToolhead = e.target.checked;
        if (toolheadGroup) toolheadGroup.visible = e.target.checked;
    });

    // G-code Modal
    const gcodeModal = document.getElementById('gcodeModal');
    document.getElementById('btnToggleGcodeModal').addEventListener('click', () => {
        gcodeModal.classList.remove('hidden');
    });
    document.getElementById('btnCloseModal').addEventListener('click', () => {
        gcodeModal.classList.add('hidden');
    });
    gcodeModal.addEventListener('click', (e) => {
        if (e.target === gcodeModal) gcodeModal.classList.add('hidden');
    });

    document.getElementById('btnCopyGcode').addEventListener('click', () => {
        const text = document.getElementById('modalGcodeText').value;
        navigator.clipboard.writeText(text).then(() => {
            alert('G-code copied to clipboard!');
        });
    });

    document.getElementById('btnDownloadGcodeModal').addEventListener('click', handleDownloadGcode);
}
