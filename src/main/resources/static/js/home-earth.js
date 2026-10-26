import * as THREE from 'three';

const canvas = document.querySelector('#home-earth-canvas');
const fallback = document.querySelector('[data-earth-fallback]');
const home = document.querySelector('[data-home-page]');
const FALLBACK_LANDMASSES = [
  [[-168, 70], [-145, 73], [-127, 57], [-115, 50], [-124, 39], [-111, 30], [-98, 18], [-83, 25], [-78, 34], [-61, 47], [-66, 59], [-91, 70], [-120, 74]],
  [[-79, 12], [-62, 10], [-48, -2], [-36, -10], [-43, -24], [-52, -39], [-68, -56], [-76, -37], [-81, -10]],
  [[-53, 82], [-28, 78], [-20, 66], [-41, 59], [-58, 68]],
  [[-12, 36], [0, 43], [16, 47], [32, 57], [51, 56], [60, 68], [95, 74], [130, 59], [155, 53], [178, 58], [164, 42], [143, 37], [130, 29], [120, 18], [107, 8], [94, 9], [82, 22], [68, 8], [54, 26], [43, 39], [28, 34], [13, 37], [2, 39], [-8, 35]],
  [[-18, 35], [2, 37], [18, 30], [34, 28], [48, 10], [43, -12], [34, -28], [18, -35], [2, -28], [-8, -17], [-17, 2], [-5, 14], [-17, 23]],
  [[113, -11], [131, -10], [146, -16], [154, -28], [143, -40], [128, -35], [115, -25]],
  [[95, 21], [105, 20], [111, 12], [105, 5], [102, -2], [107, -7], [118, -8], [123, 2], [115, 8], [112, 17], [104, 20]]
];

if (canvas && home) {
  try {
    const renderer = new THREE.WebGLRenderer({
      canvas,
      alpha: true,
      antialias: true,
      powerPreference: 'low-power'
    });
    if (fallback) fallback.hidden = true;
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5));
    renderer.setClearColor(0x000000, 0);

    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(34, 1, 0.1, 100);
    const baseCameraZ = 5.6;
    camera.position.z = baseCameraZ;

    scene.add(new THREE.AmbientLight(0xffffff, 1.25));
    const planetLight = new THREE.DirectionalLight(0xffffff, 1.1);
    planetLight.position.set(-2.4, 2.2, 4.5);
    scene.add(planetLight);

    const earth = new THREE.Group();
    const earthSurface = new THREE.Mesh(
      new THREE.SphereGeometry(1.365, 128, 96),
      new THREE.MeshBasicMaterial({
        color: '#d8edf7',
        transparent: true,
        opacity: 0.48,
        depthWrite: true
      })
    );
    earthSurface.renderOrder = 0;
    earth.add(earthSurface);
    const landDots = createLandDots(earth, FALLBACK_LANDMASSES);
    createGraticule(earth);
    const countryBoundaries = new THREE.Group();
    earth.add(countryBoundaries);
    countryBoundaries.add(createFallbackCountryLines());
    loadCountryBoundaries(countryBoundaries, landDots);

    const { pulses } = createNetwork(earth);
    scene.add(earth);
    earth.rotation.y = 0;

    let width = 0;
    let height = 0;
    let viewWidth = 0;
    let scrollProgress = 0;
    let scrollTarget = 0;
    let elapsed = 0;
    let travelStartX = 0;
    let travelMiddleX = 0;
    let travelEndX = 0;
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    const clock = new THREE.Clock();
    const smoothedPosition = new THREE.Vector3();
    let rotationOffset = 0;
    const pointer = { x: 0, y: 0, targetX: 0, targetY: 0 };
    let homePositionY = 0;
    let dragRotation = 0;
    let dragVelocity = 0;
    let isDragging = false;
    let lastPointerX = 0;
    let pointerId = null;
    let animationFrame = 0;

    function resize() {
      width = window.innerWidth;
      height = window.innerHeight;
      renderer.setSize(width, height, false);
      camera.aspect = width / Math.max(height, 1);
      camera.updateProjectionMatrix();

      const mobile = width < 768;
      const radius = mobile
        ? Math.min(width * 0.37, 150)
        : Math.min(width * 0.29, height * 0.39, 340);
      const viewHeight = 2 * Math.tan(THREE.MathUtils.degToRad(camera.fov / 2)) * baseCameraZ;
      viewWidth = viewHeight * width / Math.max(height, 1);
      earth.scale.setScalar(radius * viewHeight / Math.max(height, 1) / 1.365);

      // Place the globe by viewport coordinates so the scroll path truly crosses the screen.
      const startRatio = mobile ? 0.78 : 0.7;
      const middleRatio = 0.34;
      const endRatio = mobile ? 0.55 : 0.56;
      travelStartX = (startRatio - 0.5) * viewWidth;
      travelMiddleX = (middleRatio - 0.5) * viewWidth;
      travelEndX = (endRatio - 0.5) * viewWidth;
      const centerYRatio = mobile ? 0.68 : 0.55;
      homePositionY = (0.5 - centerYRatio) * viewHeight;
      earth.position.set(travelStartX, homePositionY, 0);
      updateScrollProgress();
    }

    function render() {
      if (document.hidden) return;

      const delta = Math.min(clock.getDelta(), 0.05);
      elapsed += delta;
      if (!reducedMotion.matches) {
        const damping = 1 - Math.exp(-3.6 * delta);
        scrollProgress += (scrollTarget - scrollProgress) * (1 - Math.exp(-4.4 * delta));
        pointer.x += (pointer.targetX - pointer.x) * (1 - Math.exp(-4.8 * delta));
        pointer.y += (pointer.targetY - pointer.y) * (1 - Math.exp(-4.8 * delta));

        dragRotation += dragVelocity * delta;
        dragVelocity *= Math.exp(-5.5 * delta);
        const targetRotationOffset = scrollProgress * 0.34 + dragRotation + pointer.x * 0.12;
        rotationOffset += (targetRotationOffset - rotationOffset) * damping;
        earth.rotation.y = elapsed * 0.08 + rotationOffset;
        const targetTilt = 0.06 + Math.sin(scrollProgress * Math.PI * 1.4) * 0.12 + pointer.y * 0.055;
        earth.rotation.x += (targetTilt * 0.6 - earth.rotation.x) * damping;

        const firstLegEnd = 0.56;
        const pathProgress = scrollProgress < firstLegEnd
          ? THREE.MathUtils.smoothstep(scrollProgress / firstLegEnd, 0, 1) * 0.56
          : 0.56 + THREE.MathUtils.smoothstep((scrollProgress - firstLegEnd) / (1 - firstLegEnd), 0, 1) * 0.44;
        const screenPathX = pathProgress < 0.56
          ? THREE.MathUtils.lerp(travelStartX, travelMiddleX, pathProgress / 0.56)
          : THREE.MathUtils.lerp(travelMiddleX, travelEndX, (pathProgress - 0.56) / 0.44);
        const vertical = Math.sin(scrollProgress * Math.PI) * 0.5;
        smoothedPosition.set(
          screenPathX + pointer.x * viewWidth * 0.025,
          homePositionY - vertical + pointer.y * 0.09,
          0
        );
        earth.position.lerp(smoothedPosition, damping);

        const targetCameraZ = baseCameraZ - Math.sin(scrollProgress * Math.PI) * 0.55;
        camera.position.z += (targetCameraZ - camera.position.z) * damping;
        const footerFade = THREE.MathUtils.smoothstep(scrollProgress, 0.72, 0.94);
        canvas.style.opacity = (1 - footerFade * 0.9).toFixed(3);
        pulses.forEach(({ curve, marker, glow, beamMaterial, impactRings, speed, phase }) => {
          const cycle = (elapsed * speed + phase) % 1;
          const active = cycle < 0.46;
          const progress = active ? cycle / 0.46 : 0;
          beamMaterial.uniforms.uHead.value = progress;
          beamMaterial.uniforms.uActive.value = active ? 1 : 0;
          marker.visible = active;
          glow.visible = active;
          if (active) {
            impactRings.forEach(({ ring }) => { ring.visible = false; });
            const point = curve.getPointAt(progress);
            marker.position.copy(point);
            glow.position.copy(point);
            const glowSize = 0.1 + Math.sin(progress * Math.PI) * 0.05;
            glow.scale.set(glowSize, glowSize, 1);
            marker.scale.setScalar(0.72 + Math.sin(progress * Math.PI) * 0.3);
            return;
          }

          const impactProgress = (cycle - 0.46) / 0.34;
          impactRings.forEach(({ ring, delay }) => {
            const rippleProgress = (impactProgress - delay) / (1 - delay);
            const visible = rippleProgress >= 0 && rippleProgress < 1;
            ring.visible = visible;
            if (!visible) return;
            ring.scale.setScalar(1 + rippleProgress * 6.5);
            ring.material.opacity = Math.sin(rippleProgress * Math.PI) * 0.22;
          });
        });
      }
      renderer.render(scene, camera);
      if (!reducedMotion.matches && !document.hidden) animationFrame = requestAnimationFrame(render);
    }

    function updateScrollProgress() {
      const scrollRange = Math.max(document.documentElement.scrollHeight - window.innerHeight, 1);
      scrollTarget = THREE.MathUtils.clamp(window.scrollY / scrollRange, 0, 1);
      const footerFade = THREE.MathUtils.smoothstep(scrollTarget, 0.72, 0.94);
      canvas.style.opacity = (1 - footerFade * 0.9).toFixed(3);
    }

    function isInteractiveTarget(target) {
      return target instanceof Element && Boolean(target.closest('a, button, input, select, textarea, [role="button"]'));
    }

    const hero = home.querySelector('.home-hero');
    hero?.addEventListener('pointermove', (event) => {
      const bounds = hero.getBoundingClientRect();
      pointer.targetX = THREE.MathUtils.clamp(((event.clientX - bounds.left) / bounds.width - 0.5) * 2, -1, 1);
      pointer.targetY = THREE.MathUtils.clamp(((event.clientY - bounds.top) / bounds.height - 0.5) * 2, -1, 1);
      if (!isDragging || event.pointerId !== pointerId) return;
      const movement = event.clientX - lastPointerX;
      lastPointerX = event.clientX;
      dragVelocity = movement * 0.012;
      dragRotation += movement * 0.004;
    });
    hero?.addEventListener('pointerdown', (event) => {
      if (isInteractiveTarget(event.target)) return;
      if (event.target !== hero && !event.target.closest('.earth-caption')) return;
      isDragging = true;
      pointerId = event.pointerId;
      lastPointerX = event.clientX;
      hero.setPointerCapture?.(pointerId);
      hero.classList.add('is-earth-dragging');
    });
    hero?.addEventListener('pointerleave', () => {
      pointer.targetX = 0;
      pointer.targetY = 0;
    });
    const stopDragging = () => {
      isDragging = false;
      pointerId = null;
      hero?.classList.remove('is-earth-dragging');
    };
    hero?.addEventListener('pointerup', stopDragging);
    hero?.addEventListener('pointercancel', stopDragging);
    hero?.addEventListener('lostpointercapture', stopDragging);

    document.addEventListener('visibilitychange', () => {
      if (document.hidden) {
        cancelAnimationFrame(animationFrame);
        return;
      }
      clock.start();
      render();
    });
    reducedMotion.addEventListener('change', () => {
      if (reducedMotion.matches) cancelAnimationFrame(animationFrame);
      else render();
    });
    window.addEventListener('scroll', updateScrollProgress, { passive: true });
    window.addEventListener('resize', resize, { passive: true });
    resize();
    render();
  } catch (error) {
    canvas.hidden = true;
    if (fallback) fallback.hidden = false;
    console.warn('DRSS earth scene could not start; using the static globe fallback.', error);
  }
}

function createEarthPointCloud() {
  const count = 8200;
  const positions = new Float32Array(count * 3);
  const colors = new Float32Array(count * 3);
  const sizes = new Float32Array(count);
  const alphas = new Float32Array(count);
  const oceanBlue = new THREE.Color('#13b9ec');
  const highlight = new THREE.Color('#9cf3ff');
  const deepBlue = new THREE.Color('#087fae');
  let seed = 7193;
  const random = () => {
    seed = (seed * 16807) % 2147483647;
    return (seed - 1) / 2147483646;
  };

  for (let index = 0; index < count; index += 1) {
    const y = 1 - (index / (count - 1)) * 2;
    const radius = Math.sqrt(Math.max(0, 1 - y * y));
    const theta = Math.PI * (3 - Math.sqrt(5)) * index;
    const pointRadius = 1.385 + (random() - 0.5) * 0.018;
    const offset = index * 3;
    positions[offset] = Math.cos(theta) * radius * pointRadius;
    positions[offset + 1] = y * pointRadius;
    positions[offset + 2] = Math.sin(theta) * radius * pointRadius;
    const latitude = Math.asin(y) * THREE.MathUtils.RAD2DEG;
    const longitude = Math.atan2(Math.cos(theta) * radius, Math.sin(theta) * radius) * THREE.MathUtils.RAD2DEG;
    const land = isLandPoint(latitude, longitude);
    const tone = random();
    const color = land
      ? (tone > 0.97 ? highlight : (tone > 0.74 ? oceanBlue : deepBlue))
      : deepBlue;
    colors[offset] = color.r;
    colors[offset + 1] = color.g;
    colors[offset + 2] = color.b;
    const sizeSeed = random();
    sizes[index] = land
      ? (sizeSeed > 0.99 ? 0.07 + random() * 0.028 : 0.022 + sizeSeed * 0.023)
      : 0.009 + sizeSeed * 0.009;
    alphas[index] = land ? 0.92 : 0.12;
  }

  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
  geometry.setAttribute('color', new THREE.Float32BufferAttribute(colors, 3));
  geometry.setAttribute('aSize', new THREE.Float32BufferAttribute(sizes, 1));
  geometry.setAttribute('aAlpha', new THREE.Float32BufferAttribute(alphas, 1));
  return new THREE.Points(geometry, new THREE.ShaderMaterial({
    transparent: true,
    depthWrite: false,
    vertexColors: true,
    blending: THREE.AdditiveBlending,
    uniforms: { time: { value: 0 } },
    vertexShader: `
      attribute float aSize;
      attribute float aAlpha;
      varying vec3 vColor;
      varying float vAlpha;
      uniform float time;
      void main() {
        vColor = color;
        vec4 mvPosition = modelViewMatrix * vec4(position, 1.0);
        float twinkle = 0.84 + 0.16 * sin(time * 0.8 + aSize * 90.0);
        vAlpha = aAlpha * twinkle;
        gl_PointSize = aSize * (250.0 / max(-mvPosition.z, 1.0)) * twinkle;
        gl_Position = projectionMatrix * mvPosition;
      }
    `,
    fragmentShader: `
      varying vec3 vColor;
      varying float vAlpha;
      void main() {
        float distanceToCenter = distance(gl_PointCoord, vec2(0.5));
        float dotShape = 1.0 - smoothstep(0.34, 0.5, distanceToCenter);
        gl_FragColor = vec4(vColor, dotShape * vAlpha);
      }
    `
  }));
}

function createEarthWireframe() {
  const grid = new THREE.Group();
  const latitudeMaterial = new THREE.LineBasicMaterial({
    color: '#14a9d4',
    transparent: true,
    opacity: 0.22,
    blending: THREE.AdditiveBlending,
    depthWrite: false
  });
  const longitudeMaterial = new THREE.LineBasicMaterial({
    color: '#5be3ff',
    transparent: true,
    opacity: 0.18,
    blending: THREE.AdditiveBlending,
    depthWrite: false
  });

  for (let latitude = -80; latitude <= 80; latitude += 20) {
    const points = [];
    for (let longitude = -180; longitude <= 180; longitude += 3) {
      points.push(earthPoint(latitude, longitude, 1.374));
    }
    const line = new THREE.Line(new THREE.BufferGeometry().setFromPoints(points), latitudeMaterial);
    line.renderOrder = 0.5;
    grid.add(line);
  }

  for (let longitude = -180; longitude < 180; longitude += 20) {
    const points = [];
    for (let latitude = -87; latitude <= 87; latitude += 3) {
      points.push(earthPoint(latitude, longitude, 1.374));
    }
    const line = new THREE.Line(new THREE.BufferGeometry().setFromPoints(points), longitudeMaterial);
    line.renderOrder = 0.5;
    grid.add(line);
  }

  return grid;
}

function isLandPoint(latitude, longitude) {
  const continents = [
    [[-168, 70], [-145, 73], [-127, 57], [-115, 50], [-124, 39], [-111, 30], [-98, 18], [-83, 25], [-78, 34], [-61, 47], [-66, 59], [-91, 70], [-120, 74]],
    [[-79, 12], [-62, 10], [-48, -2], [-36, -10], [-43, -24], [-52, -39], [-68, -56], [-76, -37], [-81, -10]],
    [[-53, 82], [-28, 78], [-20, 66], [-41, 59], [-58, 68]],
    [[-12, 36], [0, 43], [16, 47], [32, 57], [51, 56], [60, 68], [95, 74], [130, 59], [155, 53], [178, 58], [164, 42], [143, 37], [130, 29], [120, 18], [107, 8], [94, 9], [82, 22], [68, 8], [54, 26], [43, 39], [28, 34], [13, 37], [2, 39], [-8, 35]],
    [[-18, 35], [2, 37], [18, 30], [34, 28], [48, 10], [43, -12], [34, -28], [18, -35], [2, -28], [-8, -17], [-17, 2], [-5, 14], [-17, 23]],
    [[113, -11], [131, -10], [146, -16], [154, -28], [143, -40], [128, -35], [115, -25]],
    [[95, 21], [105, 20], [111, 12], [105, 5], [102, -2], [107, -7], [118, -8], [123, 2], [115, 8], [112, 17], [104, 20]]
  ];

  return continents.some((polygon) => {
    let inside = false;
    for (let current = 0, previous = polygon.length - 1; current < polygon.length; previous = current, current += 1) {
      const [currentLon, currentLat] = polygon[current];
      const [previousLon, previousLat] = polygon[previous];
      const crossesLatitude = (currentLat > latitude) !== (previousLat > latitude);
      const crossingLongitude = ((previousLon - currentLon) * (latitude - currentLat)) /
        (previousLat - currentLat) + currentLon;
      if (crossesLatitude && longitude < crossingLongitude) inside = !inside;
    }
    return inside;
  });
}

function createLandDots(earth, rings) {
  const dots = new THREE.Points(createLandDotGeometry(rings), createRoundPointMaterial('#078dff', 3.5, 1));
  dots.renderOrder = 2;
  earth.add(dots);
  return dots;
}

function createLandDotGeometry(rings) {
  const preparedRings = rings.map((ring) => {
    let minLongitude = Infinity;
    let maxLongitude = -Infinity;
    let minLatitude = Infinity;
    let maxLatitude = -Infinity;
    ring.forEach(([longitude, latitude]) => {
      minLongitude = Math.min(minLongitude, longitude);
      maxLongitude = Math.max(maxLongitude, longitude);
      minLatitude = Math.min(minLatitude, latitude);
      maxLatitude = Math.max(maxLatitude, latitude);
    });
    return {
      ring,
      minLongitude,
      maxLongitude,
      minLatitude,
      maxLatitude
    };
  });
  const positions = [];
  const step = 2.15;
  for (let latitude = -78; latitude <= 82; latitude += step) {
    for (let longitude = -180; longitude < 180; longitude += step) {
      const onLand = preparedRings.some((entry) =>
        longitude >= entry.minLongitude && longitude <= entry.maxLongitude &&
        latitude >= entry.minLatitude && latitude <= entry.maxLatitude &&
        isPointInGeoRing(longitude, latitude, entry.ring)
      );
      if (!onLand) continue;
      const point = earthPoint(latitude, longitude, 1.376);
      positions.push(point.x, point.y, point.z);
    }
  }
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
  return geometry;
}

function isPointInGeoRing(longitude, latitude, ring) {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i, i += 1) {
    const [xi, yi] = ring[i];
    const [xj, yj] = ring[j];
    const crossesLatitude = (yi > latitude) !== (yj > latitude);
    if (crossesLatitude && longitude < ((xj - xi) * (latitude - yi)) / (yj - yi) + xi) inside = !inside;
  }
  return inside;
}

function createGraticule(earth) {
  const lineMaterial = new THREE.LineBasicMaterial({
    color: '#737b80', transparent: true, opacity: 0.34, depthWrite: false
  });
  const lineGroup = new THREE.Group();
  const lineSteps = 144;
  for (let latitude = -75; latitude <= 75; latitude += 15) {
    const points = [];
    for (let step = 0; step < lineSteps; step += 1) {
      points.push(earthPoint(latitude, -180 + (step / lineSteps) * 360, 1.382));
    }
    lineGroup.add(new THREE.LineLoop(new THREE.BufferGeometry().setFromPoints(points), lineMaterial));
  }
  for (let longitude = -180; longitude < 180; longitude += 15) {
    const points = [];
    for (let step = 0; step <= lineSteps; step += 1) {
      points.push(earthPoint(-89 + (step / lineSteps) * 178, longitude, 1.382));
    }
    lineGroup.add(new THREE.Line(new THREE.BufferGeometry().setFromPoints(points), lineMaterial));
  }
  lineGroup.renderOrder = 1;
  earth.add(lineGroup);

  const nodes = [];
  for (let latitude = -75; latitude <= 75; latitude += 15) {
    for (let longitude = -180; longitude < 180; longitude += 15) {
      const point = earthPoint(latitude, longitude, 1.389);
      nodes.push(point.x, point.y, point.z);
    }
  }
  const nodeGeometry = new THREE.BufferGeometry();
  nodeGeometry.setAttribute('position', new THREE.Float32BufferAttribute(nodes, 3));
  const intersections = new THREE.Points(nodeGeometry, createRoundPointMaterial('#55c6ff', 6.2, 0.94));
  intersections.renderOrder = 3;
  earth.add(intersections);
}

function createRoundPointMaterial(color, size, opacity) {
  return new THREE.ShaderMaterial({
    transparent: true,
    depthWrite: false,
    uniforms: {
      pointColor: { value: new THREE.Color(color) },
      pointSize: { value: size },
      pointOpacity: { value: opacity }
    },
    vertexShader: `
      uniform float pointSize;
      varying float vFade;
      void main() {
        vec4 viewPosition = modelViewMatrix * vec4(position, 1.0);
        gl_Position = projectionMatrix * viewPosition;
        gl_PointSize = pointSize * (5.0 / max(1.0, -viewPosition.z));
        vFade = smoothstep(-7.2, -4.0, viewPosition.z);
      }
    `,
    fragmentShader: `
      uniform vec3 pointColor;
      uniform float pointOpacity;
      varying float vFade;
      void main() {
        float distanceFromCenter = length(gl_PointCoord - vec2(0.5));
        float core = 1.0 - smoothstep(0.17, 0.35, distanceFromCenter);
        float halo = exp(-distanceFromCenter * 10.0) * 0.28;
        float alpha = max(core, halo);
        if (alpha < 0.02) discard;
        gl_FragColor = vec4(pointColor * (1.0 + halo * 1.8), alpha * pointOpacity * vFade);
        #include <colorspace_fragment>
      }
    `
  });
}

function createNetwork(earth) {
  const nodes = [
    earthPoint(48, -125, 1.4), earthPoint(35, -72, 1.4),
    earthPoint(12, -42, 1.4), earthPoint(52, 18, 1.4),
    earthPoint(8, 72, 1.4), earthPoint(38, 112, 1.4),
    earthPoint(-24, 135, 1.4), earthPoint(-34, 18, 1.4),
    earthPoint(62, 95, 1.4)
  ];
  const edges = [
    [0, 1], [1, 2], [1, 3], [3, 4], [4, 5], [5, 6], [4, 8], [2, 7], [7, 3],
    [0, 3], [2, 3], [2, 4], [3, 5], [5, 8], [6, 7], [1, 7], [8, 6], [0, 7]
  ];
  const pulses = [];
  const glowTexture = createParticleGlow();

  nodes.forEach((point, index) => {
    const isSos = index === 4;
    const color = isSos ? '#078dff' : '#168fff';
    const marker = new THREE.Mesh(
      new THREE.SphereGeometry(isSos ? 0.028 : 0.018, 12, 10),
      new THREE.MeshBasicMaterial({ color, depthWrite: false })
    );
    marker.position.copy(point);
    earth.add(marker);
    const nodeGlow = new THREE.Sprite(new THREE.SpriteMaterial({
      map: glowTexture,
      color,
      transparent: true,
      opacity: isSos ? 0.3 : 0.18,
      blending: THREE.NormalBlending,
      depthWrite: false
    }));
    nodeGlow.position.copy(point);
    nodeGlow.scale.setScalar(isSos ? 0.105 : 0.075);
    earth.add(nodeGlow);
  });

  edges.forEach(([from, to], index) => {
    const start = nodes[from];
    const end = nodes[to];
    const midpoint = start.clone().add(end).normalize().multiplyScalar(1.57);
    const curve = new THREE.QuadraticBezierCurve3(start, midpoint, end);
    const beamMaterial = new THREE.ShaderMaterial({
      transparent: true,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
      uniforms: {
        uHead: { value: 0 },
        uActive: { value: 0 },
        uTrailLength: { value: 0.24 },
        uColor: { value: new THREE.Color(index % 3 === 0 ? '#e9f8ff' : '#299bff') }
      },
      vertexShader: `
        varying float vAlong;
        void main() {
          vAlong = uv.x;
          gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
        }
      `,
      fragmentShader: `
        uniform float uHead;
        uniform float uActive;
        uniform float uTrailLength;
        uniform vec3 uColor;
        varying float vAlong;
        void main() {
          float behindHead = uHead - vAlong;
          float inTrail = step(0.0, behindHead) * (1.0 - smoothstep(uTrailLength * 0.72, uTrailLength, behindHead));
          float tailFade = exp(-behindHead * 7.5);
          float launchFade = smoothstep(0.0, 0.09, uHead);
          float arrivalFade = 1.0 - smoothstep(0.92, 1.0, uHead);
          float alpha = inTrail * tailFade * launchFade * arrivalFade * uActive * 0.94;
          if (alpha < 0.008) discard;
          gl_FragColor = vec4(uColor * (1.0 + tailFade * 0.8), alpha);
          #include <colorspace_fragment>
        }
      `
    });
    const beam = new THREE.Mesh(new THREE.TubeGeometry(curve, 96, 0.0035, 6, false), beamMaterial);
    beam.renderOrder = 4;
    earth.add(beam);
    const pulse = new THREE.Mesh(
      new THREE.SphereGeometry(0.018, 10, 8),
      new THREE.MeshBasicMaterial({ color: '#dff5ff', transparent: true, depthWrite: false })
    );
    const pulseGlow = new THREE.Sprite(new THREE.SpriteMaterial({
      map: glowTexture,
      color: '#16a6ff',
      transparent: true,
      opacity: 0.48,
      blending: THREE.NormalBlending,
      depthWrite: false
    }));
    earth.add(pulse);
    earth.add(pulseGlow);
    pulse.visible = false;
    pulseGlow.visible = false;
    const impactRings = [0, 1].map((ringIndex) => {
      const ring = new THREE.Mesh(
        new THREE.RingGeometry(0.025, 0.032, 48),
        new THREE.MeshBasicMaterial({
          color: ringIndex === 0 ? '#92b5c7' : '#789ab4',
          transparent: true,
          opacity: 0,
          side: THREE.DoubleSide,
          blending: THREE.NormalBlending,
          depthWrite: false
        })
      );
      ring.position.copy(end.clone().multiplyScalar(1.006));
      ring.quaternion.setFromUnitVectors(new THREE.Vector3(0, 0, 1), end.clone().normalize());
      ring.visible = false;
      ring.renderOrder = 5;
      earth.add(ring);
      return { ring, delay: ringIndex * 0.18 };
    });
    pulses.push({
      curve,
      beamMaterial,
      marker: pulse,
      glow: pulseGlow,
      impactRings,
      speed: 0.14 + (index % 4) * 0.022,
      phase: index / edges.length
    });
  });

  return { pulses };
}

function createEarthMaterial() {
  const fallbackMap = createFallbackEarthTexture();
  const fallbackNight = new THREE.DataTexture(
    new Uint8Array([0, 0, 0, 255]), 1, 1, THREE.RGBAFormat
  );
  fallbackNight.needsUpdate = true;

  return new THREE.ShaderMaterial({
    uniforms: {
      dayMap: { value: fallbackMap },
      nightMap: { value: fallbackNight },
      sunDirection: { value: new THREE.Vector3(-0.3, 0.24, 1).normalize() }
    },
    vertexShader: `
      varying vec2 vUv;
      varying vec3 vNormal;
      varying vec3 vView;
      void main() {
        vUv = uv;
        vec4 viewPosition = modelViewMatrix * vec4(position, 1.0);
        vNormal = normalize(normalMatrix * normal);
        vView = normalize(-viewPosition.xyz);
        gl_Position = projectionMatrix * viewPosition;
      }
    `,
    fragmentShader: `
      uniform sampler2D dayMap;
      uniform sampler2D nightMap;
      uniform vec3 sunDirection;
      varying vec2 vUv;
      varying vec3 vNormal;
      varying vec3 vView;
      void main() {
        float daylight = dot(normalize(vNormal), normalize(sunDirection));
        float dayAmount = smoothstep(-0.2, 0.18, daylight);
        float dusk = smoothstep(-0.22, 0.02, daylight) * (1.0 - smoothstep(0.02, 0.28, daylight));
        vec3 day = texture2D(dayMap, vUv).rgb;
        float landDetail = dot(day, vec3(0.3, 0.55, 0.15));
        vec3 coolDay = mix(vec3(0.025, 0.09, 0.18), vec3(0.28, 0.62, 0.84), landDetail);
        day = mix(day, coolDay, 0.58);
        vec3 cityLights = texture2D(nightMap, vUv).rgb * vec3(1.0, 0.56, 0.25) * 1.35;
        vec3 color = mix(cityLights, day, dayAmount);
        color += day * dusk * 0.16;
        float limb = pow(1.0 - max(dot(normalize(vNormal), normalize(vView)), 0.0), 3.0);
        color += vec3(0.015, 0.18, 0.28) * limb * 0.3;
        gl_FragColor = vec4(color, 1.0);
        #include <colorspace_fragment>
      }
    `,
    lights: false
  });
}

function loadEarthMaps(earthMaterial, cloudMaterial) {
  const loader = new THREE.TextureLoader();
  loader.setCrossOrigin('anonymous');
  const mapRoot = 'https://cdn.jsdelivr.net/gh/mrdoob/three.js@r186/examples/textures/planets/';
  const configure = (texture, isColor = false) => {
    if (isColor) texture.colorSpace = THREE.SRGBColorSpace;
    texture.wrapS = THREE.RepeatWrapping;
    texture.anisotropy = 8;
    return texture;
  };

  loader.load(`${mapRoot}earth_atmos_2048.jpg`, (texture) => {
    earthMaterial.uniforms.dayMap.value = configure(texture, true);
  });
  loader.load(`${mapRoot}earth_lights_2048.png`, (texture) => {
    earthMaterial.uniforms.nightMap.value = configure(texture, true);
  });
  loader.load(`${mapRoot}earth_clouds_1024.png`, (texture) => {
    cloudMaterial.alphaMap = configure(texture);
    cloudMaterial.opacity = 0.34;
    cloudMaterial.needsUpdate = true;
  });
}

function createFallbackEarthTexture() {
  const width = 1024;
  const height = 512;
  const textureCanvas = document.createElement('canvas');
  textureCanvas.width = width;
  textureCanvas.height = height;
  const context = textureCanvas.getContext('2d');
  const ocean = context.createLinearGradient(0, 0, 0, height);
  ocean.addColorStop(0, '#091629');
  ocean.addColorStop(0.5, '#123554');
  ocean.addColorStop(1, '#071323');
  context.fillStyle = ocean;
  context.fillRect(0, 0, width, height);

  FALLBACK_LANDMASSES.forEach((polygon) => {
    context.beginPath();
    polygon.forEach(([longitude, latitude], index) => {
      const x = (longitude + 180) / 360 * width;
      const y = (90 - latitude) / 180 * height;
      if (index === 0) context.moveTo(x, y);
      else context.lineTo(x, y);
    });
    context.closePath();
    context.fillStyle = '#31576d';
    context.strokeStyle = 'rgba(78, 216, 255, 0.78)';
    context.lineWidth = 2;
    context.shadowColor = '#11c9ff';
    context.shadowBlur = 9;
    context.fill();
    context.stroke();
    context.shadowBlur = 0;
  });

  const texture = new THREE.CanvasTexture(textureCanvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.wrapS = THREE.RepeatWrapping;
  return texture;
}

function createFallbackCloudTexture() {
  const cloudCanvas = document.createElement('canvas');
  cloudCanvas.width = 512;
  cloudCanvas.height = 256;
  const context = cloudCanvas.getContext('2d');
  context.fillStyle = '#000';
  context.fillRect(0, 0, cloudCanvas.width, cloudCanvas.height);
  let seed = 4831;
  for (let index = 0; index < 170; index += 1) {
    seed = (seed * 16807) % 2147483647;
    const x = (seed / 2147483647) * cloudCanvas.width;
    seed = (seed * 16807) % 2147483647;
    const y = (seed / 2147483647) * cloudCanvas.height;
    const radius = 5 + (seed % 22);
    const cloud = context.createRadialGradient(x, y, 0, x, y, radius);
    cloud.addColorStop(0, 'rgba(255,255,255,0.34)');
    cloud.addColorStop(1, 'rgba(255,255,255,0)');
    context.fillStyle = cloud;
    context.beginPath();
    context.arc(x, y, radius, 0, Math.PI * 2);
    context.fill();
  }
  return new THREE.CanvasTexture(cloudCanvas);
}

function createFallbackCountryLines() {
  const positions = [];
  FALLBACK_LANDMASSES.forEach((polygon) => appendBoundaryRing(positions, polygon, 1.394));
  return createBoundaryLayers(new Float32Array(positions));
}

function loadCountryBoundaries(group, landDots) {
  const url = 'https://cdn.jsdelivr.net/gh/nvkelso/natural-earth-vector@master/geojson/ne_110m_admin_0_countries.geojson';
  fetch(url, { mode: 'cors' })
    .then((response) => {
      if (!response.ok) throw new Error(`Country boundary map returned ${response.status}`);
      return response.json();
    })
    .then((geoJson) => {
      const positions = [];
      const landRings = [];
      geoJson.features.forEach(({ geometry }) => {
        if (!geometry) return;
        const polygons = geometry.type === 'Polygon' ? [geometry.coordinates] : geometry.coordinates;
        polygons.forEach((polygon) => {
          if (polygon[0]) landRings.push(polygon[0]);
          polygon.forEach((ring) => appendBoundaryRing(positions, ring, 1.394));
        });
      });
      if (positions.length < 1000) return;
      group.children.forEach((child) => {
        child.geometry?.dispose();
        if (Array.isArray(child.material)) child.material.forEach((material) => material.dispose());
        else child.material?.dispose();
      });
      group.clear();
      group.add(createBoundaryLayers(new Float32Array(positions)));
      landDots.geometry.dispose();
      landDots.geometry = createLandDotGeometry(landRings);
    })
    .catch((error) => console.info('DRSS earth is using its built-in boundary map.', error));
}

function appendBoundaryRing(target, ring, radius) {
  for (let index = 0; index < ring.length - 1; index += 1) {
    const start = ring[index];
    const end = ring[index + 1];
    if (Math.abs(end[0] - start[0]) > 180) continue;
    const steps = Math.max(1, Math.ceil(Math.max(Math.abs(end[0] - start[0]), Math.abs(end[1] - start[1])) / 1.8));
    let previous = earthPoint(start[1], start[0], radius);
    for (let step = 1; step <= steps; step += 1) {
      const progress = step / steps;
      const longitude = THREE.MathUtils.lerp(start[0], end[0], progress);
      const latitude = THREE.MathUtils.lerp(start[1], end[1], progress);
      const next = earthPoint(latitude, longitude, radius);
      target.push(previous.x, previous.y, previous.z, next.x, next.y, next.z);
      previous = next;
    }
  }
}

function createBoundaryLayers(positions) {
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
  const group = new THREE.Group();
  const borders = new THREE.LineSegments(geometry, new THREE.LineBasicMaterial({
    color: '#667177', transparent: true, opacity: 0.24, depthWrite: false
  }));
  borders.renderOrder = 2;
  group.add(borders);
  return group;
}

function createSpaceGlow() {
  const glowCanvas = document.createElement('canvas');
  glowCanvas.width = 256;
  glowCanvas.height = 256;
  const context = glowCanvas.getContext('2d');
  const gradient = context.createRadialGradient(128, 128, 24, 128, 128, 128);
  gradient.addColorStop(0, 'rgba(83, 94, 100, 0.18)');
  gradient.addColorStop(0.48, 'rgba(107, 119, 125, 0.10)');
  gradient.addColorStop(0.74, 'rgba(136, 148, 153, 0.035)');
  gradient.addColorStop(1, 'rgba(158, 168, 172, 0)');
  context.fillStyle = gradient;
  context.fillRect(0, 0, glowCanvas.width, glowCanvas.height);
  const texture = new THREE.CanvasTexture(glowCanvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}

function createAtmosphereGlowTexture() {
  const glowCanvas = document.createElement('canvas');
  glowCanvas.width = 256;
  glowCanvas.height = 256;
  const context = glowCanvas.getContext('2d');
  const gradient = context.createRadialGradient(128, 128, 64, 128, 128, 128);
  gradient.addColorStop(0, 'rgba(91, 151, 184, 0)');
  gradient.addColorStop(0.64, 'rgba(111, 170, 198, 0)');
  gradient.addColorStop(0.7, 'rgba(133, 194, 218, 0.78)');
  gradient.addColorStop(0.77, 'rgba(145, 199, 218, 0.34)');
  gradient.addColorStop(0.88, 'rgba(165, 205, 220, 0.1)');
  gradient.addColorStop(1, 'rgba(165, 205, 220, 0)');
  context.fillStyle = gradient;
  context.fillRect(0, 0, glowCanvas.width, glowCanvas.height);
  const texture = new THREE.CanvasTexture(glowCanvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}

function createParticleGlow() {
  const glowCanvas = document.createElement('canvas');
  glowCanvas.width = 128;
  glowCanvas.height = 128;
  const context = glowCanvas.getContext('2d');
  const gradient = context.createRadialGradient(64, 64, 2, 64, 64, 64);
  gradient.addColorStop(0, 'rgba(225, 253, 255, 0.95)');
  gradient.addColorStop(0.12, 'rgba(79, 231, 255, 0.8)');
  gradient.addColorStop(0.38, 'rgba(18, 183, 232, 0.3)');
  gradient.addColorStop(1, 'rgba(18, 183, 232, 0)');
  context.fillStyle = gradient;
  context.fillRect(0, 0, glowCanvas.width, glowCanvas.height);
  const texture = new THREE.CanvasTexture(glowCanvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}

function createStarField() {
  const count = 150;
  const positions = new Float32Array(count * 3);
  const sizes = new Float32Array(count);
  const phases = new Float32Array(count);
  let seed = 18439;
  const random = () => {
    seed = (seed * 16807) % 2147483647;
    return (seed - 1) / 2147483646;
  };
  for (let index = 0; index < count; index += 1) {
    const angle = random() * Math.PI * 2;
    const distance = 1.5 + random() * 2.4;
    positions[index * 3] = Math.cos(angle) * distance;
    positions[index * 3 + 1] = Math.sin(angle) * distance;
    positions[index * 3 + 2] = -1.55 - random() * 0.25;
    sizes[index] = 1.0 + random() * 1.7;
    phases[index] = random() * Math.PI * 2;
  }
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
  geometry.setAttribute('aSize', new THREE.Float32BufferAttribute(sizes, 1));
  geometry.setAttribute('aPhase', new THREE.Float32BufferAttribute(phases, 1));
  return new THREE.Points(geometry, new THREE.ShaderMaterial({
    transparent: true,
    depthWrite: false,
    uniforms: { time: { value: 0 } },
    vertexShader: `
      attribute float aSize;
      attribute float aPhase;
      uniform float time;
      varying float vAlpha;
      void main() {
        vec3 drifted = position;
        drifted.x += sin(time * 0.08 + aPhase) * 0.025;
        drifted.y += cos(time * 0.06 + aPhase) * 0.02;
        vAlpha = 0.28 + 0.46 * (0.5 + 0.5 * sin(time * 0.45 + aPhase));
        vec4 mvPosition = modelViewMatrix * vec4(drifted, 1.0);
        gl_PointSize = aSize;
        gl_Position = projectionMatrix * mvPosition;
      }
    `,
    fragmentShader: `
      varying float vAlpha;
      void main() {
        float distanceToCenter = distance(gl_PointCoord, vec2(0.5));
        float dotShape = 1.0 - smoothstep(0.25, 0.5, distanceToCenter);
        gl_FragColor = vec4(vec3(0.28, 0.78, 0.94), dotShape * vAlpha);
      }
    `
  }));
}

function earthPoint(latitude, longitude, radius) {
  const lat = THREE.MathUtils.degToRad(latitude);
  const lon = THREE.MathUtils.degToRad(longitude + 90);
  return new THREE.Vector3(
    radius * Math.cos(lat) * Math.sin(lon),
    radius * Math.sin(lat),
    radius * Math.cos(lat) * Math.cos(lon)
  );
}
