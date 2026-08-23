import React from 'react';
import { Layers, Database, Cpu, Activity } from 'lucide-react';
import { Badge } from '../common/Badge';

export const Footer: React.FC = () => {
  const gatewayUrl = import.meta.env.VITE_API_GATEWAY_URL || 'http://localhost:8080';

  return (
    <footer className="bg-white border-t border-slate-200 mt-auto">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-8">
          {/* Brand Col */}
          <div className="space-y-4 md:col-span-1">
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-lg bg-slate-900 flex items-center justify-center text-white">
                <Layers className="w-4 h-4" />
              </div>
              <span className="font-bold text-base text-slate-900">CommerceCore</span>
            </div>
            <p className="text-xs text-slate-500 leading-relaxed">
              Enterprise Spring Boot Microservices E-Commerce platform architecture with unified API Gateway and Kafka event fabric.
            </p>
            <div className="pt-2">
              <Badge variant="primary" size="sm" dot>
                Gateway: {gatewayUrl}
              </Badge>
            </div>
          </div>

          {/* Microservices Architecture Info */}
          <div className="space-y-3">
            <h4 className="text-xs font-bold uppercase tracking-wider text-slate-900 flex items-center gap-1.5">
              <Cpu className="w-3.5 h-3.5 text-blue-600" />
              Backend Services
            </h4>
            <ul className="text-xs text-slate-500 space-y-2">
              <li className="flex items-center justify-between">
                <span>Auth Service</span>
                <span className="font-mono text-slate-400">:8081</span>
              </li>
              <li className="flex items-center justify-between">
                <span>User Service</span>
                <span className="font-mono text-slate-400">:8082</span>
              </li>
              <li className="flex items-center justify-between">
                <span>Inventory Service</span>
                <span className="font-mono text-slate-400">:8083</span>
              </li>
              <li className="flex items-center justify-between">
                <span>Order Service</span>
                <span className="font-mono text-slate-400">:8084</span>
              </li>
            </ul>
          </div>

          {/* Architecture Continued */}
          <div className="space-y-3">
            <h4 className="text-xs font-bold uppercase tracking-wider text-slate-900 flex items-center gap-1.5">
              <Activity className="w-3.5 h-3.5 text-emerald-600" />
              Event & Storage
            </h4>
            <ul className="text-xs text-slate-500 space-y-2">
              <li className="flex items-center justify-between">
                <span>Payment Service</span>
                <span className="font-mono text-slate-400">:8085</span>
              </li>
              <li className="flex items-center justify-between">
                <span>Notification Service</span>
                <span className="font-mono text-slate-400">:8086</span>
              </li>
              <li className="flex items-center justify-between">
                <span>Review Service</span>
                <span className="font-mono text-slate-400">:8087</span>
              </li>
              <li className="flex items-center justify-between">
                <span>Eureka Registry</span>
                <span className="font-mono text-slate-400">:8761</span>
              </li>
            </ul>
          </div>

          {/* Patterns & Guarantees */}
          <div className="space-y-3">
            <h4 className="text-xs font-bold uppercase tracking-wider text-slate-900 flex items-center gap-1.5">
              <Database className="w-3.5 h-3.5 text-purple-600" />
              Architectural Core
            </h4>
            <div className="flex flex-wrap gap-1.5">
              <span className="px-2 py-1 bg-slate-100 rounded text-[11px] text-slate-600 font-medium">
                Transactional Outbox
              </span>
              <span className="px-2 py-1 bg-slate-100 rounded text-[11px] text-slate-600 font-medium">
                Idempotency Guard
              </span>
              <span className="px-2 py-1 bg-slate-100 rounded text-[11px] text-slate-600 font-medium">
                Redis Cache-Aside
              </span>
              <span className="px-2 py-1 bg-slate-100 rounded text-[11px] text-slate-600 font-medium">
                Optimistic Locking
              </span>
              <span className="px-2 py-1 bg-slate-100 rounded text-[11px] text-slate-600 font-medium">
                Stateless JWT
              </span>
            </div>
          </div>
        </div>

        <div className="mt-10 pt-6 border-t border-slate-100 flex flex-col sm:flex-row items-center justify-between gap-4 text-xs text-slate-400">
          <p>© {new Date().getFullYear()} CommerceCore Microservices Platform. All rights reserved.</p>
          <div className="flex items-center gap-4">
            <span>Direct Gateway Mode</span>
            <span>•</span>
            <span>Zero Direct-Service Calls</span>
          </div>
        </div>
      </div>
    </footer>
  );
};
