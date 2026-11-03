import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import './style.css';
const root = document.getElementById('root');
if (!root) throw new Error('앱 루트 요소를 찾을 수 없습니다.');
createRoot(root).render(<App/>);
