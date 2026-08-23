import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { Sparkles, ArrowRight, ChevronLeft, ChevronRight, Layers, ShieldCheck, Cpu } from 'lucide-react';
import { Link } from 'react-router-dom';
import { productApi } from '../api/productApi';
import { extractApiErrorMessage } from '../api/client';
import type { ProductResponse } from '../types/product';
import { ProductGrid } from '../components/store/ProductGrid';
import { ProductFilter } from '../components/store/ProductFilter';
import { Button } from '../components/common/Button';

export const StoreFrontPage: React.FC = () => {
  const [products, setProducts] = useState<ProductResponse[]>([]);
  const [page, setPage] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(1);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const [selectedCategory, setSelectedCategory] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [sortBy, setSortBy] = useState<string>('id,desc');

  // Discovered categories list
  const [categories, setCategories] = useState<string[]>([
    'Electronics',
    'Accessories',
    'Audio',
    'Apparel',
    'Home',
  ]);

  const loadProducts = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const response = await productApi.listProducts({
        category: selectedCategory || undefined,
        page,
        size: 12,
        sort: sortBy,
      });

      if (response && response.content) {
        setProducts(response.content);
        setTotalPages(response.totalPages || 1);
        setTotalElements(response.totalElements || response.content.length);

        // Dynamically add any newly discovered categories from backend
        const backendCategories = response.content
          .map((p) => p.category)
          .filter((c): c is string => !!c && typeof c === 'string');

        if (backendCategories.length > 0) {
          setCategories((prev) => {
            const combined = Array.from(new Set([...prev, ...backendCategories]));
            return combined;
          });
        }
      } else {
        setProducts([]);
        setTotalPages(1);
        setTotalElements(0);
      }
    } catch (err) {
      setError(extractApiErrorMessage(err, 'Failed to fetch product catalogue.'));
      setProducts([]);
    } finally {
      setIsLoading(false);
    }
  }, [selectedCategory, page, sortBy]);

  useEffect(() => {
    loadProducts();
  }, [loadProducts]);

  // Client-side search filtering on currently loaded page items
  const filteredProducts = useMemo(() => {
    if (!searchQuery.trim()) {
      return products;
    }
    const q = searchQuery.toLowerCase().trim();
    return products.filter(
      (p) =>
        p.name.toLowerCase().includes(q) ||
        (p.description && p.description.toLowerCase().includes(q)) ||
        (p.category && p.category.toLowerCase().includes(q))
    );
  }, [products, searchQuery]);

  const handleCategoryChange = (category: string) => {
    setSelectedCategory(category);
    setPage(0);
  };

  const handleSortChange = (sort: string) => {
    setSortBy(sort);
    setPage(0);
  };

  const handleResetFilter = () => {
    setSelectedCategory('');
    setSearchQuery('');
    setSortBy('id,desc');
    setPage(0);
  };

  return (
    <div className="w-full">
      {/* Hero Showcase Section */}
      <section className="relative overflow-hidden bg-slate-900 text-white py-16 sm:py-20 border-b border-slate-800">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
          <div className="max-w-3xl space-y-6">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-blue-500/10 text-xs font-semibold text-blue-400 border border-blue-500/20">
              <Sparkles className="w-3.5 h-3.5" />
              <span>Spring Boot Microservices Storefront</span>
            </div>

            <h1 className="text-4xl sm:text-5xl lg:text-6xl font-extrabold tracking-tight leading-[1.1]">
              CommerceCore <br />
              <span className="bg-gradient-to-r from-blue-400 via-indigo-300 to-purple-400 bg-clip-text text-transparent">
                Product Catalogue
              </span>
            </h1>

            <p className="text-base sm:text-lg text-slate-300 leading-relaxed max-w-2xl">
              Real-time product lookup and stock status delivered through Spring Cloud Gateway with Redis Cache-Aside, synchronous payment processing, and event-driven fulfillment.
            </p>

            <div className="flex flex-wrap items-center gap-3 pt-2">
              <Link to="/cart">
                <Button variant="primary" size="lg" className="bg-blue-600 hover:bg-blue-500 text-white border-0" rightIcon={<ArrowRight className="w-4 h-4" />}>
                  View Cart
                </Button>
              </Link>
              <Link to="/account/profile">
                <Button variant="outline" size="lg" className="border-slate-700 bg-slate-800/80 text-slate-200 hover:bg-slate-800 hover:text-white">
                  Customer Account
                </Button>
              </Link>
            </div>
          </div>
        </div>

        {/* Decorative Grid */}
        <div className="absolute inset-0 bg-[radial-gradient(#3b82f6_1px,transparent_1px)] [background-size:24px_24px] opacity-10" />
      </section>

      {/* Main Catalogue Body */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10 space-y-8">
        {/* Architecture Info Cards */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="p-4 rounded-xl bg-white border border-slate-200/80 shadow-xs flex items-start gap-3">
            <div className="w-8 h-8 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
              <Layers className="w-4 h-4" />
            </div>
            <div>
              <h4 className="text-xs font-bold text-slate-900">Inventory Service (:8083)</h4>
              <p className="text-[11px] text-slate-500 mt-0.5">
                Optimistic concurrency control with Redis Cache-Aside lookup.
              </p>
            </div>
          </div>

          <div className="p-4 rounded-xl bg-white border border-slate-200/80 shadow-xs flex items-start gap-3">
            <div className="w-8 h-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0">
              <ShieldCheck className="w-4 h-4" />
            </div>
            <div>
              <h4 className="text-xs font-bold text-slate-900">Distributed Idempotency</h4>
              <p className="text-[11px] text-slate-500 mt-0.5">
                Atomic DB + Outbox commits guarantee replay safety.
              </p>
            </div>
          </div>

          <div className="p-4 rounded-xl bg-white border border-slate-200/80 shadow-xs flex items-start gap-3">
            <div className="w-8 h-8 rounded-lg bg-purple-50 text-purple-600 flex items-center justify-center shrink-0">
              <Cpu className="w-4 h-4" />
            </div>
            <div>
              <h4 className="text-xs font-bold text-slate-900">Gateway Routing (:8080)</h4>
              <p className="text-[11px] text-slate-500 mt-0.5">
                Zero direct microservice port connections from the browser.
              </p>
            </div>
          </div>
        </div>

        {/* Filter, Search and Sort Bar */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs">
          <ProductFilter
            categories={categories}
            selectedCategory={selectedCategory}
            onSelectCategory={handleCategoryChange}
            searchQuery={searchQuery}
            onSearchChange={setSearchQuery}
            sortBy={sortBy}
            onSortChange={handleSortChange}
          />
        </div>

        {/* Results Header */}
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-bold text-slate-900">
              {selectedCategory ? `${selectedCategory} Products` : 'All Products'}
            </h2>
            <p className="text-xs text-slate-500">
              {isLoading ? 'Loading products...' : `Showing ${filteredProducts.length} ${totalElements > 0 ? `of ${totalElements}` : ''} items`}
            </p>
          </div>
        </div>

        {/* Product Grid */}
        <ProductGrid
          products={filteredProducts}
          isLoading={isLoading}
          error={error}
          onRetry={loadProducts}
          onResetFilter={handleResetFilter}
        />

        {/* Pagination Controls */}
        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-2 pt-6">
            <Button
              variant="outline"
              size="sm"
              disabled={page <= 0 || isLoading}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              leftIcon={<ChevronLeft className="w-4 h-4" />}
            >
              Previous
            </Button>
            <span className="text-xs font-semibold text-slate-600 px-3">
              Page {page + 1} of {totalPages}
            </span>
            <Button
              variant="outline"
              size="sm"
              disabled={page >= totalPages - 1 || isLoading}
              onClick={() => setPage((p) => p + 1)}
              rightIcon={<ChevronRight className="w-4 h-4" />}
            >
              Next
            </Button>
          </div>
        )}
      </div>
    </div>
  );
};
