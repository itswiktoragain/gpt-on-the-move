// Name: Bank Stream
// ID: bankstream
// Description: Plays original Breaking the Bank animation on a video overlay.
// Original branching and sound are driven by Scratch blocks.
// This extension must be unsandboxed and requires an internet connection.
(function (Scratch) {
  'use strict';
  if (!Scratch.extensions.unsandboxed) {
    throw new Error('Bank Stream needs unsandboxed TurboWarp extension mode.');
  }
  const URL = 'https://raw.githubusercontent.com/itswiktoragain/gpt-on-the-move/bank-stream/breaking-the-bank-scratch/media/BreakingTheBank.mp4';
  const FPS = 30;

  class BankStream {
    constructor() {
      this.video = null;
      this.layer = null;
      this.statusElement = null;
      this.finished = true;
      this.status = 'Not started';
      this.generation = 0;
      this.endTime = 0;
      this.endFrame = 0;
      this.frameCallback = null;
      Scratch.vm.runtime.on('PROJECT_STOP_ALL', () => this.stop());
      Scratch.vm.runtime.on('RUNTIME_DISPOSED', () => this.dispose());
    }

    getInfo() {
      return {
        id: 'bankstream',
        name: 'Bank Stream',
        color1: '#3451a4',
        blocks: [
          {
            opcode: 'playSegment',
            blockType: Scratch.BlockType.COMMAND,
            text: 'play original movie frames [START] through [END]',
            arguments: {
              START: {type: Scratch.ArgumentType.NUMBER, defaultValue: 0},
              END: {type: Scratch.ArgumentType.NUMBER, defaultValue: 153}
            }
          },
          {
            opcode: 'finishedPlaying',
            blockType: Scratch.BlockType.BOOLEAN,
            text: 'movie segment finished?'
          },
          {
            opcode: 'getStatus',
            blockType: Scratch.BlockType.REPORTER,
            text: 'video status'
          },
          {
            opcode: 'stop',
            blockType: Scratch.BlockType.COMMAND,
            text: 'stop bank movie'
          }
        ]
      };
    }

    ensureVideo() {
      if (this.video) return this.video;
      const layer = document.createElement('div');
      layer.style.position = 'absolute';
      layer.style.top = '0';
      layer.style.left = '0';
      layer.style.width = '480px';
      layer.style.height = '360px';
      layer.style.background = '#fff';
      layer.style.pointerEvents = 'none';
      layer.style.overflow = 'hidden';

      const video = document.createElement('video');
      video.style.width = '480px';
      video.style.height = '360px';
      video.style.display = 'block';
      video.style.objectFit = 'fill';
      video.style.pointerEvents = 'none';
      video.autoplay = false;
      video.muted = true;
      video.playsInline = true;
      video.preload = 'auto';
      video.disablePictureInPicture = true;

      const message = document.createElement('div');
      message.style.position = 'absolute';
      message.style.inset = '0';
      message.style.background = '#fff';
      message.style.color = '#222';
      message.style.font = '16px Arial,sans-serif';
      message.style.display = 'flex';
      message.style.alignItems = 'center';
      message.style.justifyContent = 'center';
      message.style.textAlign = 'center';
      message.style.padding = '24px';
      message.style.boxSizing = 'border-box';
      message.style.pointerEvents = 'none';
      message.textContent = 'Loading original Flash animation...';

      layer.appendChild(video);
      layer.appendChild(message);
      const overlay = Scratch.renderer.addOverlay(layer, 'scale');
      overlay.container.style.pointerEvents = 'none';
      this.video = video;
      this.layer = layer;
      this.statusElement = message;

      video.addEventListener('error', () => {
        const err = video.error;
        this.problem('The streamed animation could not be loaded (' +
          (err ? err.code : 'network') + '). Check your connection or the GitHub media branch.');
      });
      video.src = URL;
      video.load();
      return video;
    }

    problem(description) {
      this.status = description;
      this.finished = true;
      if (this.video) this.video.pause();
      if (this.statusElement) {
        this.statusElement.textContent = description;
        this.statusElement.style.display = 'flex';
        this.statusElement.style.color = '#b00020';
      }
    }

    async waitForEvent(video, event, condition, token) {
      if (condition()) return;
      await new Promise((resolve, reject) => {
        let timeout;
        const clean = () => {
          clearTimeout(timeout);
          video.removeEventListener(event, success);
          video.removeEventListener('error', failure);
        };
        const success = () => { clean(); resolve(); };
        const failure = () => { clean(); reject(new Error('Video loading failed')); };
        timeout = setTimeout(() => { clean(); reject(new Error('Video request timed out')); }, 30000);
        video.addEventListener(event, success, {once: true});
        video.addEventListener('error', failure, {once: true});
      });
      if (token !== this.generation) throw new Error('Playback replaced');
    }

    async playSegment({START, END}) {
      const token = ++this.generation;
      this.finished = false;
      this.status = 'Loading video...';
      const first = Math.max(0, Math.min(5394, Math.round(Number(START) || 0)));
      const last = Math.max(first, Math.min(5394, Math.round(Number(END) || 0)));
      const video = this.ensureVideo();
      video.pause();
      if (this.statusElement) {
        this.statusElement.textContent = 'Loading original Flash animation...';
        this.statusElement.style.color = '#222';
        this.statusElement.style.display = 'flex';
      }
      try {
        await this.waitForEvent(video, 'loadedmetadata', () => video.readyState >= 1, token);
        if (token !== this.generation) return;
        const beginning = first / FPS;
        if (Math.abs(video.currentTime - beginning) > 0.015) {
          video.currentTime = beginning;
          await this.waitForEvent(video, 'seeked', () => !video.seeking, token);
        }
        if (token !== this.generation) return;
        this.endFrame = last;
        this.endTime = (last + 0.65) / FPS;
        this.status = 'Playing';
        this.finished = false;
        if (this.statusElement) this.statusElement.style.display = 'none';
        await video.play();
        if (token !== this.generation) return;
        this.watchFrame(token);
      } catch (error) {
        if (token === this.generation) {
          this.problem('Cannot play original animation: ' + String(error.message || error));
        }
      }
    }

    watchFrame(token) {
      const video = this.video;
      const check = () => {
        if (!video || token !== this.generation || this.finished) return;
        if (video.currentTime >= this.endTime || video.ended) {
          video.pause();
          video.currentTime = this.endFrame / FPS;
          this.finished = true;
          this.status = 'Finished';
        } else if (typeof video.requestVideoFrameCallback === 'function') {
          this.frameCallback = video.requestVideoFrameCallback(check);
        } else {
          this.frameCallback = setTimeout(check, 25);
        }
      };
      if (typeof video.requestVideoFrameCallback === 'function') {
        this.frameCallback = video.requestVideoFrameCallback(check);
      } else {
        this.frameCallback = setTimeout(check, 25);
      }
    }

    finishedPlaying() { return this.finished; }
    getStatus() { return this.status; }

    stop() {
      ++this.generation;
      this.finished = true;
      this.status = 'Stopped';
      if (this.video) {
        this.video.pause();
        if (this.frameCallback !== null) {
          if (typeof this.video.cancelVideoFrameCallback === 'function') {
            this.video.cancelVideoFrameCallback(this.frameCallback);
          } else {
            clearTimeout(this.frameCallback);
          }
        }
      }
      this.frameCallback = null;
    }

    dispose() {
      this.stop();
      if (this.layer) Scratch.renderer.removeOverlay(this.layer);
      if (this.video) {
        this.video.removeAttribute('src');
        this.video.load();
      }
      this.video = null;
      this.layer = null;
    }
  }
  Scratch.extensions.register(new BankStream());
})(Scratch);
