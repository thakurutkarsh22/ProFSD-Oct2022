# Java LLD Interview Questions — Complete Guide (7 YOE)

Compiled from LeetCode Discuss, GeeksforGeeks, CrackingWalnuts, workat.tech, lldproblems.com, InterviewBit, GitHub's awesome-low-level-design (23k+ stars), and multiple Medium articles based on real candidate experiences (2025–2026).

---

## Table of Contents

- [Booking & Reservation Systems](#booking--reservation-systems)
- [Financial & Payment Systems](#financial--payment-systems)
- [Platform Scale / Marketplace Systems](#platform-scale--marketplace-systems)
- [Games & Simulations](#games--simulations)
- [Real-World Utility Systems](#real-world-utility-systems)
- [Foundational / Infrastructure Systems](#foundational--infrastructure-systems)
- [Company-Wise Quick Reference](#company-wise-quick-reference)
- [Must-Do Overlap (Highest ROI)](#must-do-overlap-highest-roi)
- [Concurrency & Multithreading LLD Questions](#concurrency--multithreading-lld-questions)
- [Design Patterns You Must Know](#design-patterns-you-must-know)
- [What Interviewers Evaluate at 7 YOE](#what-interviewers-evaluate-at-7-yoe)
- [Resources](#resources)

---

## Booking & Reservation Systems

| # | Problem | Companies That Ask | Source |
|---|---------|-------------------|--------|
| 1 | **Parking Lot System** | Amazon, Google, Microsoft, Adobe, Uber, Grab, Gojek, Flipkart, Salesforce, Swiggy, Zomato, Razorpay, Atlassian, PhonePe, Paytm | [LLD Mastery](https://www.lowleveldesignmastery.com/blog/low-level-design-interview-questions/), [Amazon LLD](https://medium.com/@prashant558908/most-common-amazon-low-level-design-interview-questions-0201056a9fca), [Uber LLD](https://medium.com/@prashant558908/uber-low-level-design-interview-questions-from-recent-interviews-7035fadfcb3d), [Salesforce LLD](https://medium.com/@prashant558908/salesforce-low-level-design-questions-from-recent-interviews-3009c3a58f78), [Microsoft LLD](https://medium.com/@prashant558908/microsoft-most-frequent-low-level-design-questions-from-recent-interviews-b9ba1da387df) |
| 2 | **Movie Ticket Booking (BookMyShow)** | Amazon, Uber, Microsoft, Flipkart, Salesforce, Swiggy | [Amazon LLD](https://medium.com/@prashant558908/most-common-amazon-low-level-design-interview-questions-0201056a9fca), [Uber LLD](https://medium.com/@prashant558908/uber-low-level-design-interview-questions-from-recent-interviews-7035fadfcb3d), [Microsoft LLD](https://medium.com/@prashant558908/microsoft-most-frequent-low-level-design-questions-from-recent-interviews-b9ba1da387df) |
| 3 | **Hotel Management System** | Amazon, Google, Flipkart | [awesome-low-level-design](https://github.com/ashishps1/awesome-low-level-design), [CrackingWalnuts](https://crackingwalnuts.com/low-level-design) |
| 4 | **Car Rental System** | Uber, Amazon | [Uber LLD](https://medium.com/@prashant558908/uber-low-level-design-interview-questions-from-recent-interviews-7035fadfcb3d) |
| 5 | **Meeting Room Reservation** | Uber, Salesforce, Microsoft | [Uber LLD](https://medium.com/@prashant558908/uber-low-level-design-interview-questions-from-recent-interviews-7035fadfcb3d), [Salesforce LLD](https://medium.com/@prashant558908/salesforce-low-level-design-questions-from-recent-interviews-3009c3a58f78) |
| 6 | **Concert Ticket Booking** | Flipkart, Amazon | [CrackingWalnuts](https://crackingwalnuts.com/low-level-design) |
| 7 | **Doctor Appointment Booking (Practo)** | Flipkart | [Flipkart LLD](https://medium.com/@prashant558908/flipkart-low-level-design-interview-questions-from-recent-machine-coding-rounds-976f106f6368) |
| 8 | **Gym Slot Booking System** | Flipkart | [Flipkart LLD](https://medium.com/@prashant558908/flipkart-low-level-design-interview-questions-from-recent-machine-coding-rounds-976f106f6368) |
| 9 | **Restaurant Management / Food Ordering** | Amazon, Uber, Swiggy, Zomato | [Amazon LLD](https://medium.com/@prashant558908/most-common-amazon-low-level-design-interview-questions-0201056a9fca), [CrackingWalnuts](https://crackingwalnuts.com/low-level-design) |
| 10 | **Airline Management System** | Amazon, Google | [awesome-low-level-design](https://github.com/ashishps1/awesome-low-level-design) |
| 11 | **Course Registration System** | Flipkart, Amazon | [CrackingWalnuts](https://crackingwalnuts.com/low-level-design) |

---

## Financial & Payment Systems

| # | Problem | Companies That Ask | Source |
|---|---------|-------------------|--------|
| 12 | **Splitwise (Expense Sharing)** | Flipkart, Amazon, Swiggy, Cred, Razorpay | [Flipkart LLD](https://medium.com/@prashant558908/flipkart-low-level-design-interview-questions-from-recent-machine-coding-rounds-976f106f6368), [workat.tech](https://workat.tech/machine-coding/article/how-to-practice-for-machine-coding-kp0oj3sw2jca), [Swiggy interviews](https://www.codingkaro.in/jobs-internships/leetcode-interview-experience/Swiggy) |
| 13 | **Digital Wallet / Payment Wallet** | Flipkart, PhonePe, Paytm, Razorpay, Cred | [Flipkart LLD](https://medium.com/@prashant558908/flipkart-low-level-design-interview-questions-from-recent-machine-coding-rounds-976f106f6368), [TechWithKP](https://techwithkp.com/digital-wallet-design-machine-coding-round-solution/), [Ultimate LLD List](https://chakresh0108.medium.com/ultimate-list-of-lld-machine-coding-concurrency-design-questions-for-interviews-95efe1001bfe) |
| 14 | **Payment Gateway System** | Razorpay, PhonePe, Paytm, Cred | [Crio Masterclass](https://www.crio.do/masterclass/register/lld-of-payment-apps-razorpay-phonepe/), [CrackingWalnuts](https://crackingwalnuts.com/low-level-design) |
| 15 | **ATM Machine** | Goldman Sachs, JPMorgan, Amazon | [CrackingWalnuts](https://crackingwalnuts.com/low-level-design), [awesome-low-level-design](https://github.com/ashishps1/awesome-low-level-design) |
| 16 | **Vending Machine** | Amazon, Microsoft | [CrackingWalnuts](https://crackingwalnuts.com/low-level-design) |
| 17 | **Stock Brokerage (Zerodha/Groww)** | Amazon, Groww, Goldman Sachs | [Amazon LLD](https://medium.com/@prashant558908/most-common-amazon-low-level-design-interview-questions-0201056a9fca) |
| 18 | **Banking System** | Goldman Sachs, JPMorgan, PayTM | [Ultimate LLD List](https://chakresh0108.medium.com/ultimate-list-of-lld-machine-coding-concurrency-design-questions-for-interviews-95efe1001bfe) |
| 19 | **Buy Now Pay Later (BNPL)** | Flipkart | [Flipkart LLD](https://medium.com/@prashant558908/flipkart-low-level-design-interview-questions-from-recent-machine-coding-rounds-976f106f6368) |
| 20 | **Billing & Discount Engine** | Flipkart, Amazon | [Flipkart LLD](https://medium.com/@prashant558908/flipkart-low-level-design-interview-questions-from-recent-machine-coding-rounds-976f106f6368), [CrackingWalnuts](https://crackingwalnuts.com/low-level-design) |
| 21 | **Customer Loyalty Program** | Flipkart | [Flipkart LLD](https://medium.com/@prashant558908/flipkart-low-level-design-interview-questions-from-recent-machine-coding-rounds-976f106f6368) |
| 22 | **Pizza Pricing System** | Amazon | [Amazon LLD](https://medium.com/@prashant558908/most-common-amazon-low-level-design-interview-questions-0201056a9fca) |

---

## Platform Scale / Marketplace Systems

| # | Problem | Companies That Ask | Source |
|---|---------|-------------------|--------|
| 23 | **Ride Sharing (Uber/Ola)** | Flipkart, Uber, Ola, Gojek | [Flipkart LLD](https://medium.com/@prashant558908/flipkart-low-level-design-interview-questions-from-recent-machine-coding-rounds-976f106f6368), [GFG Flipkart Experience](https://www.geeksforgeeks.org/interview-experiences/flipkart-machine-coding-round-experience/) |
| 24 | **Food Delivery (Swiggy/Zomato)** | Amazon, Uber, Swiggy, Zomato | [Amazon LLD](https://medium.com/@prashant558908/most-common-amazon-low-level-design-interview-questions-0201056a9fca), [Uber LLD](https://medium.com/@prashant558908/uber-low-level-design-interview-questions-from-recent-interviews-7035fadfcb3d) |
| 25 | **Online Shopping / E-Commerce** | Flipkart, Amazon, Walmart | [Flipkart LLD](https://medium.com/@prashant558908/flipkart-low-level-design-interview-questions-from-recent-machine-coding-rounds-976f106f6368), [Walmart Interview Guide](https://www.codinginterview.com/guide/walmart-interview/) |
| 26 | **Order & Inventory Management** | Flipkart, Amazon, Walmart | [Flipkart LLD](https://medium.com/@prashant558908/flipkart-low-level-design-interview-questions-from-recent-machine-coding-rounds-976f106f6368) |
| 27 | **Online Auction (eBay)** | Amazon, eBay | [CrackingWalnuts](https://crackingwalnuts.com/low-level-design) |
| 28 | **Peer-to-Peer Delivery System** | Flipkart | [Flipkart LLD](https://medium.com/@prashant558908/flipkart-low-level-design-interview-questions-from-recent-machine-coding-rounds-976f106f6368) |
| 29 | **Delivery Service (with agents)** | Flipkart, Swiggy, Zomato | [Flipkart LLD](https://medium.com/@prashant558908/flipkart-low-level-design-interview-questions-from-recent-machine-coding-rounds-976f106f6368) |
| 30 | **Social Media Feed** | Meta/Facebook, Google, Twitter | [CrackingWalnuts](https://crackingwalnuts.com/low-level-design) |
| 31 | **Stack Overflow (Q&A)** | Amazon, Atlassian | [CrackingWalnuts](https://crackingwalnuts.com/low-level-design) |
| 32 | **Music Streaming (Spotify)** | Google, Amazon, Spotify | [CrackingWalnuts](https://crackingwalnuts.com/low-level-design) |
| 33 | **CricInfo (Live Score)** | Flipkart, Swiggy | [CrackingWalnuts](https://crackingwalnuts.com/low-level-design) |
| 34 | **Notification System** | Atlassian, Amazon, Flipkart | [Atlassian LLD](https://www.linkedin.com/posts/ankur-dhawan01_sde1-sde2-sde3-activity-7357987471994859522-idzn), [CrackingWalnuts](https://crackingwalnuts.com/low-level-design) |
| 35 | **Chat Room / Messaging** | Meta, Google, Atlassian | [CrackingWalnuts](https://crackingwalnuts.com/low-level-design) |
| 36 | **Dating App** | Flipkart | [Flipkart LLD](https://medium.com/@prashant558908/flipkart-low-level-design-interview-questions-from-recent-machine-coding-rounds-976f106f6368) |
| 37 | **Bug Bounty Management** | Flipkart | [Flipkart LLD](https://medium.com/@prashant558908/flipkart-low-level-design-interview-questions-from-recent-machine-coding-rounds-976f106f6368) |

---

## Games & Simulations

| # | Problem | Companies That Ask | Source |
|---|---------|-------------------|--------|
| 38 | **Chess Game** | Amazon, Microsoft, Google, Salesforce | [Amazon LLD](https://medium.com/@prashant558908/most-common-amazon-low-level-design-interview-questions-0201056a9fca), [Microsoft LLD](https://medium.com/@prashant558908/microsoft-most-frequent-low-level-design-questions-from-recent-interviews-b9ba1da387df), [Salesforce LLD](https://medium.com/@prashant558908/salesforce-low-level-design-questions-from-recent-interviews-3009c3a58f78) |
| 39 | **Snake & Ladder** | Flipkart, Goldman Sachs, Swiggy | [workat.tech](https://workat.tech/machine-coding/article/how-to-practice-for-machine-coding-kp0oj3sw2jca), [GFG Goldman Sachs](https://www.geeksforgeeks.org/goldman-sachs-interview-for-experienced/) |
| 40 | **Tic Tac Toe** | Microsoft, Amazon | [Microsoft LLD](https://medium.com/@prashant558908/microsoft-most-frequent-low-level-design-questions-from-recent-interviews-b9ba1da387df) |
| 41 | **Battleship** | Google, Amazon | [CrackingWalnuts](https://crackingwalnuts.com/low-level-design) |

---

## Real-World Utility Systems

| # | Problem | Companies That Ask | Source |
|---|---------|-------------------|--------|
| 42 | **Elevator System** | Microsoft, Salesforce, Amazon, Google | [Microsoft LLD](https://medium.com/@prashant558908/microsoft-most-frequent-low-level-design-questions-from-recent-interviews-b9ba1da387df), [Salesforce LLD](https://medium.com/@prashant558908/salesforce-low-level-design-questions-from-recent-interviews-3009c3a58f78), [Amazon LLD](https://medium.com/@prashant558908/most-common-amazon-low-level-design-interview-questions-0201056a9fca) |
| 43 | **Library Management System** | Flipkart, Amazon | [Flipkart LLD](https://medium.com/@prashant558908/flipkart-low-level-design-interview-questions-from-recent-machine-coding-rounds-976f106f6368) |
| 44 | **Task Management (Trello/Jira)** | Atlassian, Flipkart | [Atlassian LLD](https://www.linkedin.com/posts/ankur-dhawan01_sde1-sde2-sde3-activity-7357987471994859522-idzn), [workat.tech](https://workat.tech/machine-coding/article/how-to-practice-for-machine-coding-kp0oj3sw2jca) |
| 45 | **Text Editor / Word Processor** | Microsoft, Uber | [Microsoft LLD](https://medium.com/@prashant558908/microsoft-most-frequent-low-level-design-questions-from-recent-interviews-b9ba1da387df), [Uber LLD](https://medium.com/@prashant558908/uber-low-level-design-interview-questions-from-recent-interviews-7035fadfcb3d) |
| 46 | **Spreadsheet Engine (Excel)** | Microsoft | [Microsoft LLD](https://medium.com/@prashant558908/microsoft-most-frequent-low-level-design-questions-from-recent-interviews-b9ba1da387df), [LeetCode Discuss](https://leetcode.com/discuss/post/1993084/spreadhseet-lld-microsoft-by-alok1981997-re0c/) |
| 47 | **Smart Home Controller** | Google, Amazon | [CrackingWalnuts](https://crackingwalnuts.com/low-level-design) |
| 48 | **Traffic Signal System** | Google, Uber | [CrackingWalnuts](https://crackingwalnuts.com/low-level-design) |
| 49 | **Container Orchestrator (K8s-like)** | Microsoft | [Microsoft LLD](https://medium.com/@prashant558908/microsoft-most-frequent-low-level-design-questions-from-recent-interviews-b9ba1da387df) |
| 50 | **Locker/Warehouse Management** | Amazon | [Amazon LLD](https://medium.com/@prashant558908/most-common-amazon-low-level-design-interview-questions-0201056a9fca) |

---

## Foundational / Infrastructure Systems

| # | Problem | Companies That Ask | Source |
|---|---------|-------------------|--------|
| 51 | **LRU Cache** | Google, Amazon, Microsoft, Meta, Adobe, Netflix, Salesforce | [LLD Mastery](https://www.lowleveldesignmastery.com/blog/low-level-design-interview-questions/), [Salesforce LLD](https://medium.com/@prashant558908/salesforce-low-level-design-questions-from-recent-interviews-3009c3a58f78) |
| 52 | **LFU Cache** | Salesforce, Google, Amazon | [Salesforce LLD](https://medium.com/@prashant558908/salesforce-low-level-design-questions-from-recent-interviews-3009c3a58f78) |
| 53 | **Rate Limiter** | Atlassian, Salesforce, Amazon, Uber | [Atlassian interview](https://www.ambitionbox.com/interviews/atlassian-question/design-and-implement-a-rate-limiter-Tzn9HZFS), [Salesforce LLD](https://medium.com/@prashant558908/salesforce-low-level-design-questions-from-recent-interviews-3009c3a58f78) |
| 54 | **Logging Framework (log4j)** | Amazon, Cisco, Swiggy | [Ultimate LLD List](https://chakresh0108.medium.com/ultimate-list-of-lld-machine-coding-concurrency-design-questions-for-interviews-95efe1001bfe), [Stackademic](https://blog.stackademic.com/lld-7-logging-framework-low-level-design-3f2ee3101c6f) |
| 55 | **Hit Counter (Multi-threaded)** | Uber, Microsoft | [Uber LLD](https://medium.com/@prashant558908/uber-low-level-design-interview-questions-from-recent-interviews-7035fadfcb3d), [Microsoft LLD](https://medium.com/@prashant558908/microsoft-most-frequent-low-level-design-questions-from-recent-interviews-b9ba1da387df) |
| 56 | **Connection Pool** | Salesforce, Amazon | [Salesforce LLD](https://medium.com/@prashant558908/salesforce-low-level-design-questions-from-recent-interviews-3009c3a58f78) |
| 57 | **In-Memory File System** | Uber, Amazon | [Uber LLD](https://medium.com/@prashant558908/uber-low-level-design-interview-questions-from-recent-interviews-7035fadfcb3d) |
| 58 | **URL Shortener** | Atlassian, Google, Amazon | [Atlassian LLD](https://www.linkedin.com/posts/ankur-dhawan01_sde1-sde2-sde3-activity-7357987471994859522-idzn) |
| 59 | **Unix Find Command** | Amazon | [Amazon LLD](https://medium.com/@prashant558908/most-common-amazon-low-level-design-interview-questions-0201056a9fca) |
| 60 | **Job Scheduler** | Microsoft, Salesforce, Atlassian | [Microsoft LLD](https://medium.com/@prashant558908/microsoft-most-frequent-low-level-design-questions-from-recent-interviews-b9ba1da387df), [Atlassian LLD](https://www.linkedin.com/posts/ankur-dhawan01_sde1-sde2-sde3-activity-7357987471994859522-idzn) |
| 61 | **Leaderboard System** | Uber | [Uber LLD](https://medium.com/@prashant558908/uber-low-level-design-interview-questions-from-recent-interviews-7035fadfcb3d) |
| 62 | **Train Platform Management** | Uber | [Uber LLD](https://medium.com/@prashant558908/uber-low-level-design-interview-questions-from-recent-interviews-7035fadfcb3d) |
| 63 | **Google Search Autocomplete** | Microsoft, Google | [Microsoft LLD](https://medium.com/@prashant558908/microsoft-most-frequent-low-level-design-questions-from-recent-interviews-b9ba1da387df) |
| 64 | **RPC Framework** | Salesforce | [Salesforce LLD](https://medium.com/@prashant558908/salesforce-low-level-design-questions-from-recent-interviews-3009c3a58f78) |
| 65 | **In-Memory Cache (Custom Eviction)** | Salesforce | [Salesforce LLD](https://medium.com/@prashant558908/salesforce-low-level-design-questions-from-recent-interviews-3009c3a58f78) |
| 66 | **Pub/Sub Message Broker** | Atlassian, Salesforce, Amazon | [Atlassian LLD](https://www.linkedin.com/posts/ankur-dhawan01_sde1-sde2-sde3-activity-7357987471994859522-idzn), [CrackingWalnuts](https://crackingwalnuts.com/low-level-design) |
| 67 | **API Gateway** | Atlassian, Amazon | [Atlassian LLD](https://www.linkedin.com/posts/ankur-dhawan01_sde1-sde2-sde3-activity-7357987471994859522-idzn), [CrackingWalnuts](https://crackingwalnuts.com/low-level-design) |
| 68 | **Circuit Breaker** | Amazon, Netflix | [CrackingWalnuts](https://crackingwalnuts.com/low-level-design) |
| 69 | **Thread Pool Executor** | Amazon, Goldman Sachs | [CrackingWalnuts](https://crackingwalnuts.com/low-level-design) |
| 70 | **Workflow Engine** | Atlassian, Salesforce | [CrackingWalnuts](https://crackingwalnuts.com/low-level-design) |
| 71 | **Dictionary App (Trie-based)** | Microsoft | [Microsoft LLD](https://medium.com/@prashant558908/microsoft-most-frequent-low-level-design-questions-from-recent-interviews-b9ba1da387df) |
| 72 | **Stack with Increment Op** | Salesforce | [Salesforce LLD](https://medium.com/@prashant558908/salesforce-low-level-design-questions-from-recent-interviews-3009c3a58f78) |

---

## Company-Wise Quick Reference

### Amazon
Unix Find Command, Pizza Pricing, Parking Lot, Locker Management, Chess, Splitwise, Food Ordering, Stock Broker, BookMyShow, Elevator

> Source: [Amazon LLD — Medium](https://medium.com/@prashant558908/most-common-amazon-low-level-design-interview-questions-0201056a9fca)

### Microsoft
Text Editor/Word, Spreadsheet/Excel, Elevator (obsessed!), Container Orchestrator, Dictionary, Chess, Parking Lot, Job Scheduler, BookMyShow, Hit Counter, Autocomplete, Tic Tac Toe

> Source: [Microsoft LLD — Medium](https://medium.com/@prashant558908/microsoft-most-frequent-low-level-design-questions-from-recent-interviews-b9ba1da387df)

### Uber
Hit Counter, Meeting Room, File System, Leaderboard, Train Platform, BookMyShow, Parking Lot, Food Ordering, Text Editor, Car Rental

> Source: [Uber LLD — Medium](https://medium.com/@prashant558908/uber-low-level-design-interview-questions-from-recent-interviews-7035fadfcb3d)

### Flipkart
Billing & Discounts, Food Order Management, Gym Booking, P2P Delivery, Payment Wallet, Doctor Booking, Bug Bounty, BNPL, Library, Loyalty Program, Delivery Service, Order Management, Dating App, Ride Sharing

> Source: [Flipkart LLD — Medium](https://medium.com/@prashant558908/flipkart-low-level-design-interview-questions-from-recent-machine-coding-rounds-976f106f6368)

### Salesforce
Elevator, Connection Pool, Parking Lot, LRU/LFU Cache, Meeting Room, RPC Framework, Stack with Increment, Custom Eviction Cache, BookMyShow, Chess, Rate Limiter

> Source: [Salesforce LLD — Medium](https://medium.com/@prashant558908/salesforce-low-level-design-questions-from-recent-interviews-3009c3a58f78)

### Atlassian
Jira/Project Management, Rate Limiter, Notification System, API Gateway, URL Shortener, Messaging System, Job Scheduler

> Source: [Atlassian LLD — LinkedIn](https://www.linkedin.com/posts/ankur-dhawan01_sde1-sde2-sde3-activity-7357987471994859522-idzn), [AmbitionBox](https://www.ambitionbox.com/interviews/atlassian-question/design-and-implement-a-rate-limiter-Tzn9HZFS)

### Google
Parking Lot, LRU Cache, Autocomplete, Social Media Feed

> Source: [LLD Mastery](https://www.lowleveldesignmastery.com/blog/low-level-design-interview-questions/), [Coudo AI](https://www.coudo.ai/companies/google/lld-interview-questions)

### Goldman Sachs
Snake & Ladder, ATM, Banking System, Thread Pool

> Source: [GFG Goldman Sachs](https://www.geeksforgeeks.org/goldman-sachs-interview-for-experienced/), [Goldman Sachs Java Interview](https://medium.com/@nitttyn5/goldman-sachs-java-backend-interview-38-lpa-5-rounds-that-broke-my-brain-heres-exactly-what-3acb82fa3566)

### Swiggy / Zomato
Splitwise, Cart with Payment, Food Delivery, Parking Lot

> Source: [Swiggy Interviews — CodingKaro](https://www.codingkaro.in/jobs-internships/leetcode-interview-experience/Swiggy), [Ultimate LLD List](https://chakresh0108.medium.com/ultimate-list-of-lld-machine-coding-concurrency-design-questions-for-interviews-95efe1001bfe)

### PhonePe / Razorpay / Cred
Digital Wallet, Payment Gateway, Splitwise, Rate Limiter

> Source: [TechWithKP](https://techwithkp.com/digital-wallet-design-machine-coding-round-solution/), [Crio Masterclass](https://www.crio.do/masterclass/register/lld-of-payment-apps-razorpay-phonepe/)

### Netflix / Adobe
LRU Cache, Recommendation System, Parking Lot

> Source: [LLD Mastery](https://www.lowleveldesignmastery.com/blog/low-level-design-interview-questions/), [InterviewNode](http://www.interviewnode.com/post/top-25-low-level-design-lld-questions-in-ml-interviews-at-faang-companies)

---

## Must-Do Top 10 (Highest ROI)

Problems asked across the most companies + covering all major categories and pattern families. These give you the best return on prep time:

| Priority | Problem | # of Companies | Why It's High ROI |
|----------|---------|----------------|-------------------|
| 1 | **Parking Lot** | 15+ | Most asked LLD question, period. Covers Strategy, Factory, Singleton, class hierarchy. |
| 2 | **LRU Cache** | 7+ | Data structure + design. HashMap + LinkedList. Foundational for all cache problems. |
| 3 | **Splitwise** | 5+ | Financial logic, Strategy pattern, debt simplification graph algorithm. |
| 4 | **BookMyShow** | 6+ | Concurrency (seat locking with TTL), Observer, State pattern. |
| 5 | **Elevator System** | 5+ | State machine + scheduling strategy. Microsoft is obsessed with this. |
| 6 | **Snake & Ladder** | 3+ | Quick win, game design, Builder pattern. Often the warm-up LLD question. |
| 7 | **Rate Limiter** | 4+ | Infrastructure + concurrency. Token Bucket / Sliding Window with Strategy. |
| 8 | **Logging Framework** | 3+ | Chain of Responsibility, Singleton, Strategy — three patterns in one system. |
| 9 | **Ride Sharing / Food Delivery** | 4+ | Marketplace, three-sided system, State machine + Observer + Strategy. |
| 10 | **Thread Pool from scratch** | 3+ | Deep concurrency: core pool, max pool, bounded queue, rejection policies. |

---

## Concurrency & Multithreading LLD Questions

Critical for 7 YOE — companies expect deep concurrency knowledge at this level.

| # | Problem | What It Tests |
|---|---------|---------------|
| 1 | **Producer-Consumer** (bounded buffer) | `wait()`/`notify()`, `BlockingQueue` |
| 2 | **Thread Pool from scratch** | Core pool, max pool, bounded queue, rejection policies |
| 3 | **Read-Write Lock** | Concurrent readers, exclusive writers |
| 4 | **Dining Philosophers** | Deadlock avoidance strategies |
| 5 | **Rate Limiter (thread-safe)** | Token bucket with `AtomicInteger` / `Semaphore` |
| 6 | **Print Odd-Even with 2 Threads** | `wait()`/`notify()`, shared state |
| 7 | **Concurrent LRU Cache** | `ConcurrentHashMap` + lock striping |
| 8 | **Blocking Queue from scratch** | `ReentrantLock` + `Condition` |
| 9 | **CountDownLatch / CyclicBarrier** use cases | Coordination primitives |
| 10 | **Scheduled Task Executor** | `ScheduledExecutorService`, `DelayQueue` |
| 11 | **Connection Pool** | `Semaphore`, Object Pool pattern |
| 12 | **CompletableFuture pipelines** | Async composition, error handling |

---

## Design Patterns You Must Know

### Behavioral (Very High frequency in LLD)
Strategy, Observer, State, Command, Chain of Responsibility, Template Method, Iterator, Mediator, Visitor, Memento

### Creational (High frequency)
Factory / Factory Method, Singleton, Builder, Prototype, Abstract Factory

### Structural (Medium-High frequency)
Proxy, Decorator, Composite, Adapter, Facade, Flyweight, Bridge

---

## What Interviewers Evaluate at 7 YOE

At senior level, it's not just about solving the problem — they expect senior-level thinking:

1. **Requirement Clarification** — asking the right questions before coding
2. **Clean Entity Modeling** — nouns become classes, verbs become methods
3. **SOLID Principles** — especially Open/Closed and Single Responsibility
4. **Design Pattern Selection** — and the ability to justify *why* Strategy over State, etc.
5. **Concurrency Safety** — thread-safe designs without over-synchronizing
6. **Extensibility** — "how would you add feature X without modifying existing code?"
7. **Trade-off Discussion** — performance vs readability, flexibility vs complexity

### Interview Framework (recommended structure)

| Step | Time | What To Do |
|------|------|------------|
| 1. Clarify Requirements | 3-5 min | Ask about scope, constraints, edge cases |
| 2. Identify Core Entities | 5 min | Extract nouns as classes, verbs as methods |
| 3. Design Class Structure | 10-15 min | Apply SOLID principles and design patterns |
| 4. Implement Key Methods | 15-25 min | Focus on core logic first |
| 5. Discuss Trade-offs | 5 min | Show senior-level architectural thinking |

---

## Resources

| Resource | URL |
|----------|-----|
| awesome-low-level-design (GitHub, 23k+ stars) | https://github.com/ashishps1/awesome-low-level-design |
| CrackingWalnuts (67 problems with patterns) | https://crackingwalnuts.com/low-level-design |
| LLD Problems | https://www.lldproblems.com/ |
| workat.tech Machine Coding | https://workat.tech/machine-coding/practice |
| LeetCode LLD Discussion | https://leetcode.com/discuss/post/1395180/low-level-design-approach-by-thewal-e6vx/ |
| InterviewBit LLD Questions | https://interviewbit.com/low-level-design-interview-questions |
| Low Level Design Mastery | https://www.lowleveldesignmastery.com/blog/low-level-design-interview-questions/ |
| Amazon LLD Questions (Medium) | https://medium.com/@prashant558908/most-common-amazon-low-level-design-interview-questions-0201056a9fca |
| Microsoft LLD Questions (Medium) | https://medium.com/@prashant558908/microsoft-most-frequent-low-level-design-questions-from-recent-interviews-b9ba1da387df |
| Uber LLD Questions (Medium) | https://medium.com/@prashant558908/uber-low-level-design-interview-questions-from-recent-interviews-7035fadfcb3d |
| Flipkart LLD Questions (Medium) | https://medium.com/@prashant558908/flipkart-low-level-design-interview-questions-from-recent-machine-coding-rounds-976f106f6368 |
| Salesforce LLD Questions (Medium) | https://medium.com/@prashant558908/salesforce-low-level-design-questions-from-recent-interviews-3009c3a58f78 |
| Ultimate LLD + Concurrency List (Medium) | https://chakresh0108.medium.com/ultimate-list-of-lld-machine-coding-concurrency-design-questions-for-interviews-95efe1001bfe |
| GFG LLD Interview Questions | https://www.geeksforgeeks.org/top-low-level-system-designlld-interview-questions-2024/ |

---

*Last updated: April 2026. All company mappings are based on real candidate interview experiences from 2025–2026.*
