const TextToSpeech = {
    synth: null,
    currentUtterance: null,

    init() {
        if (!this.synth) {
            this.synth = window.speechSynthesis;
        }
        return this.synth !== null;
    },

    isSupported() {
        return 'speechSynthesis' in window;
    },

    getVoices() {
        if (!this.init()) return [];
        return this.synth.getVoices();
    },

    speak(text, options = {}) {
        if (!this.isSupported()) {
            console.warn('浏览器不支持语音合成功能');
            return false;
        }

        if (!text || text.trim() === '') {
            console.warn('没有可播报的内容');
            return false;
        }

        this.stop();

        const cleanText = this.cleanText(text);

        const defaultOptions = {
            lang: 'zh-CN',
            rate: 1,
            pitch: 1,
            volume: 1
        };

        const config = { ...defaultOptions, ...options };

        const utterance = new SpeechSynthesisUtterance(cleanText);
        utterance.lang = config.lang;
        utterance.rate = config.rate;
        utterance.pitch = config.pitch;
        utterance.volume = config.volume;

        utterance.onstart = () => {
            console.log('语音播报开始');
        };

        utterance.onend = () => {
            console.log('语音播报完成');
        };

        utterance.onerror = (event) => {
            console.error('语音播报错误:', event.error);
        };

        this.currentUtterance = utterance;
        this.synth.speak(utterance);

        return true;
    },

    stop() {
        if (this.synth) {
            this.synth.cancel();
            this.currentUtterance = null;
        }
    },

    pause() {
        if (this.synth) {
            this.synth.pause();
        }
    },

    resume() {
        if (this.synth) {
            this.synth.resume();
        }
    },

    isSpeaking() {
        return this.synth ? this.synth.speaking : false;
    },

    cleanText(text) {
        if (!text) return '';

        let cleaned = text.replace(/```[\s\S]*?```/g, (match) => {
            return match.replace(/```\w*\n?/g, '').replace(/```/g, '');
        });

        cleaned = cleaned.replace(/`([^`]+)`/g, '$1');

        cleaned = cleaned.replace(/\*\*([^*]+)\*\*/g, '$1');
        cleaned = cleaned.replace(/__([^_]+)__/g, '$1');

        cleaned = cleaned.replace(/\*([^*]+)\*/g, '$1');
        cleaned = cleaned.replace(/_([^_]+)_/g, '$1');

        cleaned = cleaned.replace(/\[([^\]]+)\]\([^)]+\)/g, '$1');

        cleaned = cleaned.replace(/!\[([^\]]*)\]\([^)]+\)/g, '$1');

        cleaned = cleaned.replace(/^[\s]*[-*+][\s]+/gm, '');
        cleaned = cleaned.replace(/^[\s]*\d+[\.。)][\s]+/gm, '');

        cleaned = cleaned.replace(/^#{1,6}[\s]+/gm, '');

        cleaned = cleaned.replace(/^[\s]*>[>\s]*/gm, '');

        cleaned = cleaned.replace(/^[-*_]{3,}$/gm, '');

        cleaned = cleaned.replace(/<[^>]+>/g, '');

        cleaned = cleaned.replace(/\s+/g, ' ').trim();

        return cleaned;
    }
};

TextToSpeech.init();
