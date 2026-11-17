# CommerceCore

### Full-Stack E-Commerce Platform | Spring Boot 3 + React 19

CommerceCore is a full-stack e-commerce application featuring a Spring Boot backend, React storefront, administrative dashboard, and containerized development environment.

## Features

### Authentication and Security
- JWT access and refresh token authentication
- Role-based authorization for administrators and customers
- Email verification and password recovery
- Remember-me functionality
- Password management
- Environment-variable-based configuration for sensitive credentials

### Product Catalog
- Hierarchical product categories
- Product search, filtering, sorting, and pagination
- Multiple product images
- Discount pricing
- Featured, latest, and best-selling product listings

### Shopping Experience
- Shopping cart management
- Wishlist functionality
- Saved delivery addresses
- Checkout workflow
- Order history and order tracking

### Orders and Payments
- Mock payment processing supporting card, UPI, net banking, cash on delivery, and wallet flows
- Order status management
- Customer order cancellation
- Administrative refund workflows
- Transactional email notifications

**Note:** Payment processing is simulated. The application does not currently provide live payment gateway integration.

### Admin Dashboard
- Revenue and sales visualizations
- Category-level analytics
- Product management
- Category management
- Order management
- Customer management
- CSV export

### Development and Operations
- Docker-based application setup
- GitHub Actions CI workflow
- API documentation with OpenAPI
- Postman API collection
- Architecture and database documentation
- Deployment guides for Railway and Render

## Technology Stack

| Layer | Technologies |
|---|---|
| Backend | Java 21, Spring Boot 3.3, Spring Security 6 |
| Authentication | JWT, Spring Security |
| Persistence | Spring Data JPA, Hibernate, MySQL 8 |
| Backend Utilities | Lombok, MapStruct, Jakarta Validation, Spring Mail |
| API Documentation | Springdoc OpenAPI, Postman |
| Frontend | React 19, Vite 6, JavaScript |
| Routing and State | React Router 6, Redux Toolkit |
| UI | Tailwind CSS, Framer Motion, React Icons |
| Forms and Networking | React Hook Form, Axios |
| Analytics | Recharts |
| Testing | JUnit 5, Mockito |
| DevOps | Docker, Docker Compose, GitHub Actions |

## Project Structure

```text
CommerceCore/
├── commercecore-backend/        # Spring Boot REST API
├── commercecore-frontend/       # React storefront and admin console
├── docs/
│   ├── ARCHITECTURE.md       # Architecture and request flows
│   ├── ER-DIAGRAM.md         # Database relationships
│   └── DEPLOYMENT.md         # Deployment instructions
├── postman/
│   └── CommerceCore.postman_collection.json
├── .github/
│   └── workflows/
│       └── ci-cd.yml        # Continuous integration
├── docker-compose.yml
├── LICENSE
└── README.md
```



## Architecture

CommerceCore uses a layered application architecture.

**Backend:** REST controllers, service classes, repositories, JPA entities, DTOs, and Spring Security.

**Frontend:** React components, feature-based state management, API clients, customer-facing pages, and administrative views.

**Database:** MySQL with Hibernate/JPA for persistence.

**Deployment:** Docker configurations for the backend, frontend, and database.

For additional details, see:

- [System Architecture](docs/ARCHITECTURE.md)
- [Entity-Relationship Diagram](docs/ER-DIAGRAM.md)
- [Deployment Guide](docs/DEPLOYMENT.md)

## Getting Started

### Prerequisites

- Java 21
- Node.js and npm
- MySQL 8, or Docker with Docker Compose
- Git

### 1. Clone the Repository

```bash
git clone https://github.com/prem0312/CommerceCore.git
cd CommerceCore
```

### 2. Configure Environment Variables

Refer to the example environment files:

- `commercecore-backend/.env.example`
- `commercecore-frontend/.env.example`

The backend requires configuration for database credentials, JWT signing, and the administrator account. Mail configuration may also be required for email-related features.

Never commit real passwords, API keys, or JWT secrets.

### 3. Run the Application

CommerceCore can be started using Docker Compose or by running the backend and frontend separately.

#### Option 1: Docker Compose (Recommended)

**Prerequisites:** Docker Desktop and Git.

**1. Clone the repository**

```bash
git clone https://github.com/prem0312/CommerceCore.git
cd CommerceCore
```

**2. Configure environment variables**

Copy the example environment files and update the required credentials:

```bash
cp commercecore-backend/.env.example commercecore-backend/.env
cp commercecore-frontend/.env.example commercecore-frontend/.env
```

Set your own database password, JWT secret, administrator password, and mail configuration where required.

**3. Start the application**

```bash
docker compose up --build -d
```

**4. Check the running services**

```bash
docker compose ps
```

Open the frontend at the localhost address published by Docker Compose.

**5. Stop the application**

```bash
docker compose down
```

#### Option 2: Manual Setup

**Prerequisites:** Java 21, Maven, Node.js, npm, and MySQL 8.

**1. Start the backend**

Configure MySQL and supply the required backend environment variables, then run:

```bash
cd commercecore-backend
mvn spring-boot:run
```

**2. Start the frontend**

Open a new terminal from the repository root:

```bash
cd commercecore-frontend
npm install
npm run dev
```

Open the local development URL displayed by Vite.

#### Additional Documentation

- [Backend Setup Guide](commercecore-backend/README.md)
- [Frontend Setup Guide](commercecore-frontend/README.md)
- [Deployment Guide](docs/DEPLOYMENT.md)

**Note:** Configure all required environment variables before starting. The exact Docker ports and startup behavior depend on the repository configuration.

## Testing and CI/CD

The repository includes backend unit tests, frontend build tooling, and a GitHub Actions workflow.

The CI configuration is located at:

`.github/workflows/ci-cd.yml`

Passing CI checks and successful local builds should be verified before describing the application as production-ready.

## Future Enhancements

- Integrate a real payment provider such as Stripe or Razorpay
- Add coupon and promotional discount management
- Support product variants such as size and color
- Introduce advanced search for larger product catalogs
- Add real-time order status notifications
- Expand automated integration and end-to-end tests
- Improve deployment security and observability
- Add internationalization and multi-currency support

## Project Background and Attribution

The implementation provides the foundation for the e-commerce functionality, including the backend, storefront, administrative interface, and associated documentation.

This repository retains the MIT license and attribution.

Further enhancements and independently implemented features will be documented as development continues.

## License

Licensed under the [MIT License](LICENSE).

---

**CommerceCore** — An open-source full-stack e-commerce application adapted for continued development, learning, and portfolio demonstration.